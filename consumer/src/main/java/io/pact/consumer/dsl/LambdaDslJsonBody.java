package io.pact.consumer.dsl;

public class LambdaDslJsonBody extends LambdaDslObject {

    private final PactDslJsonBody dslPart;

    LambdaDslJsonBody(final PactDslJsonBody dslPart) {
        super(dslPart);
        this.dslPart = dslPart;
    }

    public DslPart build() {
        return dslPart;
    }
}
