package com.runtimeverification.rvpredict.smt.formula;

import com.runtimeverification.rvpredict.log.ReadonlyEventInterface;
import com.runtimeverification.rvpredict.smt.visitors.Visitor;

public class ConcretePhiVariable extends BooleanVariable {
    /**
     * Prefix for naming variables belonging to this class.
     */
    private static final String PHI_C = "phi_c";

    public ConcretePhiVariable(ReadonlyEventInterface event) {
       super(event.getEventId());
    }

    @Override
    public void accept(Visitor visitor) throws Exception {
        visitor.visit(this);
    }

    @Override
    public String getNamePrefix() {
        return PHI_C;
    }
}
