package au.com.dius.pact.provider.junitsupport.loader

import io.pact.core.model.Consumer
import io.pact.core.model.PactReader
import io.pact.core.model.Provider
import io.pact.core.model.RequestResponsePact
import io.pact.core.model.UrlSource
import io.pact.core.support.Auth
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@SuppressWarnings('LineLength')
class PactUrlLoaderSpec extends Specification {

  @PactUrl(urls = ['http://123.666', 'http://localhost:1234'])
  static class TestClass1 { }

  @PactUrl(urls = ['http://localhost:1234'], auth = @Authentication(username = 'fred', password = '1234'))
  static class TestClass2 { }

  @PactUrl(urls = ['http://localhost:1234'], auth = @Authentication(token = '1234abcd'))
  static class TestClass3 { }

  @PactUrl(urls = ['http://localhost:1234'], auth = @Authentication(token = '${my.token}'))
  static class TestClass4 { }

  def 'loads a pact from each URL'() {
    given:
    def loader = new PactUrlLoader(['http://123.456', 'http://localhost:1234'] as String[], null)
    loader.pactReader = Mock(PactReader)
    def pact1 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer1'))
    def pact2 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer2'))

    when:
    def pacts = loader.load('bob')

    then:
    loader.pactReader.loadPact(new UrlSource('http://123.456'), [:]) >> pact1
    loader.pactReader.loadPact(new UrlSource('http://localhost:1234'), [:]) >> pact2
    pacts == [pact1, pact2]
  }

  def 'sets the description appropriately'() {
    given:
    def loader = new PactUrlLoader(['http://123.456', 'http://localhost:1234'] as String[], null)

    expect:
    loader.description() == 'URL([http://123.456, http://localhost:1234])'
  }

  def 'loads a pact from the provided annotation'() {
    given:
    def loader = new PactUrlLoader(TestClass1.getAnnotation(PactUrl))
    loader.pactReader = Mock(PactReader)
    def pact1 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer1'))
    def pact2 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer2'))

    when:
    def pacts = loader.load('bob')

    then:
    loader.pactReader.loadPact(new UrlSource('http://123.666'), [:]) >> pact1
    loader.pactReader.loadPact(new UrlSource('http://localhost:1234'), [:]) >> pact2
    pacts == [pact1, pact2]
  }

  def 'loads a pact with basic auth'() {
    given:
    def loader = new PactUrlLoader(TestClass2.getAnnotation(PactUrl))
    loader.pactReader = Mock(PactReader)
    def pact1 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer1'))

    when:
    def pacts = loader.load('bob')

    then:
    loader.pactReader.loadPact(new UrlSource('http://localhost:1234'), [authentication: new Auth.BasicAuthentication('fred', '1234')]) >> pact1
    pacts == [pact1]
  }

  def 'loads a pact with bearer token'() {
    given:
    def loader = new PactUrlLoader(TestClass3.getAnnotation(PactUrl))
    loader.pactReader = Mock(PactReader)
    def pact1 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer1'))

    when:
    def pacts = loader.load('bob')

    then:
    loader.pactReader.loadPact(new UrlSource('http://localhost:1234'), [authentication: new Auth.BearerAuthentication('1234abcd')]) >> pact1
    pacts == [pact1]
  }

  @RestoreSystemProperties
  def 'loads the auth values from system properties'() {
    given:
    def loader = new PactUrlLoader(TestClass4.getAnnotation(PactUrl))
    System.setProperty('my.token', '1234567890')
    loader.pactReader = Mock(PactReader)
    def pact1 = new RequestResponsePact(new Provider('bob'), new Consumer('consumer1'))

    when:
    def pacts = loader.load('bob')

    then:
    loader.pactReader.loadPact(new UrlSource('http://localhost:1234'), [authentication: new Auth.BearerAuthentication('1234567890')]) >> pact1
    pacts == [pact1]
  }
}
