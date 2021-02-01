package io.pact.consumer.junit

import io.pact.consumer.dsl.PactDslWithProvider
import io.pact.core.model.RequestResponsePact
import io.pact.core.model.annotations.Pact
import io.pact.core.model.matchingrules.EqualsMatcher
import groovyx.net.http.FromServer
import groovyx.net.http.HttpBuilder
import org.junit.Rule
import org.junit.Test

@SuppressWarnings(['PublicInstanceField', 'JUnitPublicNonTestMethod', 'FactoryMethodName'])
class TextXMLContentTypeTest {

  @Rule
  public final PactProviderRule mockProvider = new PactProviderRule('xml_provider', this)

  @Pact(consumer= 'xml_consumer')
  RequestResponsePact createPact(PactDslWithProvider builder) {
    def pact = builder
      .uponReceiving('a request with text/xml content')
      .path('/attr')
      .method('POST')
      .body('''<?xml version="1.0" encoding="UTF-8"?>
             <providerService version="1.0">
               <attribute1>
                 <newattribute>
                     <date month="11" year="2019"/>
                     <name><![CDATA[Surname Name]]></name>
                 </newattribute>
                 <newattribute2>
                   <countryCode>RO</countryCode>
                   <hiddenData>ABCD***************010101</hiddenData>
                 </newattribute2>
               </attribute1>
             </providerService>
        ''')
      .willRespondWith()
      .status(201)
      .toPact()
    pact.interactions.first().request.matchingRules.addCategory('body')
      .addRule('$.providerService.attribute1.newattribute.name', EqualsMatcher.INSTANCE)
      .addRule('$.providerService.attribute1.newattribute2.hiddenData', EqualsMatcher.INSTANCE)
    pact
  }

  @Test
  @PactVerification
  void runTest1() {
    def http = HttpBuilder.configure { request.uri = mockProvider.url }
    def xml = '''<?xml version="1.0" encoding="UTF-8"?>
       <providerService version="1.0">
         <attribute1>
           <newattribute>
               <date month="11" year="2019"/>
             <name><![CDATA[Surname Name]]></name>
           </newattribute>
           <newattribute2>
             <countryCode>RO</countryCode>
             <hiddenData>ABCD***************010101</hiddenData>
           </newattribute2>
         </attribute1>
       </providerService>
    '''

    http.post {
      request.uri.path = '/attr'
      request.body = xml
      request.contentType = 'application/xml'
      response.success { FromServer fs, Object body ->
        assert fs.statusCode == 201
      }
    }
  }
}
