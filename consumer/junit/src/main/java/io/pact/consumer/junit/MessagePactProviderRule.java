package io.pact.consumer.junit;

import io.pact.consumer.MessagePactBuilder;
import io.pact.core.model.PactSpecVersion;
import io.pact.core.model.ProviderState;
import io.pact.core.model.annotations.Pact;
import io.pact.core.model.annotations.PactDirectory;
import io.pact.core.model.messaging.Message;
import io.pact.core.model.messaging.MessagePact;
import io.pact.core.support.BuiltToolConfig;
import io.pact.core.support.expressions.DataType;
import org.apache.commons.lang3.StringUtils;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.pact.core.support.expressions.ExpressionParser.parseExpression;

/**
 * A junit rule that wraps every test annotated with {@link PactVerification}.
 */
public class MessagePactProviderRule extends ExternalResource {
	
	private final String provider;
	private final Object testClassInstance;
	private byte[] message;
	private Map<String, Message> providerStateMessages;
	private MessagePact messagePact;
	private Map<String, Object> metadata;

	/**
	 * @param testClassInstance
	 */
	public MessagePactProviderRule(Object testClassInstance) {
		this(null, testClassInstance);
	}

	public MessagePactProviderRule(String provider, Object testClassInstance) {
		this.provider = provider;
		this.testClassInstance = testClassInstance;
	}

	/* (non-Javadoc)
	 * @see org.junit.rules.ExternalResource#apply(org.junit.runners.model.Statement, org.junit.runner.Description)
	 */
	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {

			@Override
			public void evaluate() throws Throwable {
				PactVerifications pactVerifications = description.getAnnotation(PactVerifications.class);
				if (pactVerifications != null) {
					evaluatePactVerifications(pactVerifications, base, description);
					return;
				}

				PactVerification pactDef = description.getAnnotation(PactVerification.class);
				// no pactVerification? execute the test normally
				if (pactDef == null) {
					base.evaluate();
					return;
				}

				Message providedMessage = null;
				Map<String, Message> pacts;
				if (StringUtils.isNoneEmpty(pactDef.fragment())) {
          Optional<Method> possiblePactMethod = findPactMethod(pactDef);
          if (possiblePactMethod.isEmpty()) {
            base.evaluate();
            return;
          }

          pacts = new HashMap<>();
          Method method = possiblePactMethod.get();
          Pact pact = method.getAnnotation(Pact.class);
          MessagePactBuilder builder = MessagePactBuilder.consumer(
          		Objects.toString(parseExpression(pact.consumer(), DataType.RAW))).hasPactWith(provider);
          messagePact = (MessagePact) method.invoke(testClassInstance, builder);
          for (Message message : messagePact.getMessages()) {
            pacts.put(message.getProviderStates().stream().map(ProviderState::getName).collect(Collectors.joining()),
							message);
          }
        } else {
          pacts = parsePacts();
        }

        if (pactDef.value().length == 2 && !pactDef.value()[1].trim().isEmpty()) {
          providedMessage = pacts.get(pactDef.value()[1].trim());
        } else if (!pacts.isEmpty()) {
          providedMessage = pacts.values().iterator().next();
        }

				if (providedMessage == null) {
					base.evaluate();
					return;
				}

				setMessage(providedMessage, description);
				try {
					base.evaluate();
					PactDirectory pactDirectory = testClassInstance.getClass().getAnnotation(PactDirectory.class);
					if (pactDirectory != null) {
						messagePact.write(pactDirectory.value(), PactSpecVersion.V3);
					} else {
						messagePact.write(BuiltToolConfig.INSTANCE.getPactDirectory(), PactSpecVersion.V3);
					}
				} catch (Throwable t) {
					throw t;
				}
			}
		};
	}

	private void evaluatePactVerifications(PactVerifications pactVerifications, Statement base, Description description)
			throws Throwable {

		if (provider == null) {
			throw new UnsupportedOperationException("This provider name cannot be null when using @PactVerifications");
		}

		Optional<PactVerification> possiblePactVerification = findPactVerification(pactVerifications);
		if (possiblePactVerification.isEmpty()) {
			base.evaluate();
			return;
		}

		PactVerification pactVerification = possiblePactVerification.get();
		Optional<Method> possiblePactMethod = findPactMethod(pactVerification);
		if (possiblePactMethod.isEmpty()) {
			throw new UnsupportedOperationException("Could not find method with @Pact for the provider " + provider);
		}

		Method method = possiblePactMethod.get();
		Pact pact = method.getAnnotation(Pact.class);
		MessagePactBuilder builder = MessagePactBuilder.consumer(
				Objects.toString(parseExpression(pact.consumer(), DataType.RAW))).hasPactWith(provider);
		MessagePact messagePact = (MessagePact) method.invoke(testClassInstance, builder);
		setMessage(messagePact.getMessages().get(0), description);
		base.evaluate();
		messagePact.write(BuiltToolConfig.INSTANCE.getPactDirectory(), PactSpecVersion.V3);
	}

	private Optional<PactVerification> findPactVerification(PactVerifications pactVerifications) {
		PactVerification[] pactVerificationValues = pactVerifications.value();
		return Arrays.stream(pactVerificationValues).filter(p -> {
			String[] providers = p.value();
			if (providers.length != 1) {
				throw new IllegalArgumentException(
						"Each @PactVerification must specify one and only provider when using @PactVerifications");
			}
			String provider = providers[0];
			return provider.equals(this.provider);
		}).findFirst();
	}

	private Optional<Method> findPactMethod(PactVerification pactVerification) {
		String pactFragment = pactVerification.fragment();
		for (Method method : testClassInstance.getClass().getMethods()) {
			Pact pact = method.getAnnotation(Pact.class);
			if (pact != null && provider.equals(parseExpression(pact.provider(), DataType.RAW))
					&& (pactFragment.isEmpty() || pactFragment.equals(method.getName()))) {
				JUnitTestSupport.conformsToMessagePactSignature(method);
				return Optional.of(method);
			}
		}
		return Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Message> parsePacts() {
        if (providerStateMessages == null) {
        	providerStateMessages = new HashMap<>();
            for (Method m: testClassInstance.getClass().getMethods()) {
                if (conformsToSignature(m)) {
	                Pact pact = m.getAnnotation(Pact.class);
	                if (pact != null) {
	                	String provider = Objects.toString(parseExpression(pact.provider(), DataType.RAW));
	                	if (provider != null && !provider.trim().isEmpty()) {
	                		MessagePactBuilder builder = MessagePactBuilder.consumer(pact.consumer()).hasPactWith(provider);
	                		List<Message> messages;
	                		try {
	                			messagePact = (MessagePact) m.invoke(testClassInstance, builder);
		                		messages = messagePact.getMessages();
	                		} catch (Exception e) {
		                        throw new RuntimeException("Failed to invoke pact method", e);
		                    }

	                		for (Message message : messages) {
	                			if (message.getProviderStates().isEmpty()) {
													providerStateMessages.put("", message);
												} else {
	                				for (ProviderState state : message.getProviderStates()) {
														providerStateMessages.put(state.getName(), message);
													}
												}
	                		}
	                	}
	                }
                }
            }
        }

        return providerStateMessages;
	}

    /**
     * validates method signature as described at {@link Pact}
     */
    private boolean conformsToSignature(Method m) {
        Pact pact = m.getAnnotation(Pact.class);
        boolean conforms =
            pact != null
            && MessagePact.class.isAssignableFrom(m.getReturnType())
            && m.getParameterTypes().length == 1
            && m.getParameterTypes()[0].isAssignableFrom(MessagePactBuilder.class);

        if (!conforms && pact != null) {
            throw new UnsupportedOperationException("Method " + m.getName() +
                " does not conform required method signature 'public MessagePact xxx(MessagePactBuilder builder)'");
        }
        return conforms;
    }

	public byte[] getMessage() {
		if (message == null) {
			throw new UnsupportedOperationException("Message was not created and cannot be retrieved." +
								" Check @Pact and @PactVerification match.");
		}
		return message;
	}

	public Map<String, Object> getMetadata() {
		if (metadata == null) {
			throw new UnsupportedOperationException("Message metadata was not created and cannot be retrieved." +
								" Check @Pact and @PactVerification match.");
		}
		return metadata;
	}

	private void setMessage(Message message, Description description)
			throws InvocationTargetException, IllegalAccessException {

		this.message = message.contentsAsBytes();
		this.metadata = message.getMetaData();
		Method messageSetter;
		try {
			messageSetter = description.getTestClass().getMethod("setMessage", byte[].class);
		} catch (Exception e) {
			//ignore
			return;
		}
		messageSetter.invoke(testClassInstance, message.contentsAsBytes());
	}
}
