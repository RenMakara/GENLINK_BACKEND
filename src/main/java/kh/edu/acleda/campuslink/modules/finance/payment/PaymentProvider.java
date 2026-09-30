package kh.edu.acleda.campuslink.modules.finance.payment;

/** The seam between GenLink KH's wallet and any payment network. */
public interface PaymentProvider {

    /** Authorize a charge and report the outcome. Never moves money itself. */
    PaymentResult charge(PaymentInstruction instruction);
}
