package io.pact.provider.gradle

import io.pact.core.pactbroker.PactBrokerClient
import io.pact.core.pactbroker.PactBrokerClientConfig
import io.pact.core.pactbroker.RequestFailedException
import com.github.michaelbull.result.Ok
import groovy.io.FileType
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.TaskAction

/**
 * Task to push pact files to a pact broker
 */
@SuppressWarnings(['Println', 'AbcMetric'])
class PactPublishTask extends DefaultTask {

    @TaskAction
    void publishPacts() {
        if (!project.pact.publish) {
            throw new GradleScriptException('You must add a pact publish configuration to your build before you can ' +
                'use the pactPublish task', null)
        }

        PactPublish pactPublish = project.pact.publish
        if (pactPublish.pactDirectory == null) {
            pactPublish.pactDirectory = project.file("${project.buildDir}/pacts")
        }
        def version = pactPublish.consumerVersion ?: pactPublish.providerVersion
        if (version == null) {
          version = project.version
        } else if (version instanceof Closure) {
          version = version.call()
        }

        def brokerConfig = project.pact.broker ?: project.pact.publish
        def options = [:]
        if (StringUtils.isNotEmpty(brokerConfig.pactBrokerToken)) {
            options.authentication = [brokerConfig.pactBrokerAuthenticationScheme ?: 'bearer',
                                      brokerConfig.pactBrokerToken]
        }
        else if (StringUtils.isNotEmpty(brokerConfig.pactBrokerUsername)) {
          options.authentication = [brokerConfig.pactBrokerAuthenticationScheme ?: 'basic',
                                    brokerConfig.pactBrokerUsername, brokerConfig.pactBrokerPassword]
        }
        def brokerClient = new PactBrokerClient(brokerConfig.pactBrokerUrl, options, new PactBrokerClientConfig())
        File pactDirectory = pactPublish.pactDirectory as File
        boolean anyFailed = false
        pactDirectory.eachFileMatch(FileType.FILES, ~/.*\.json/) { pactFile ->
          if (pactFileIsExcluded(pactPublish, pactFile)) {
            println("Not publishing '${pactFile.name}' as it matches an item in the excluded list")
          } else {
            def result
            if (pactPublish.tags) {
              print "Publishing '${pactFile.name}' with tags ${pactPublish.tags.join(', ')} ... "
            } else {
              print "Publishing '${pactFile.name}' ... "
            }
            result = brokerClient.uploadPactFile(pactFile, version.toString(), pactPublish.tags)
            if (result instanceof Ok) {
              println('OK')
            } else {
              println("Failed - ${result.error.message}")
              if (result.error instanceof RequestFailedException && result.error.body) {
                println(result.error.body)
              }
              anyFailed = true
            }
          }
        }

        if (anyFailed) {
          throw new GradleScriptException('One or more of the pact files were rejected by the pact broker', null)
        }
    }

  static boolean pactFileIsExcluded(PactPublish pactPublish, File pactFile) {
    pactPublish.excludes.any {
      FilenameUtils.getBaseName(pactFile.name) ==~ it
    }
  }
}
