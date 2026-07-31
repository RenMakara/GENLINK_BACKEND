package kh.edu.acleda.campuslink.modules.finance.payment;

/**
 * The seam between GenLink KH's wallet and any payment network - ACLEDA, ABA, Wing, TrueMoney,
 * Visa, MasterCard, KHQR, or whatever comes next. A provider decides one thing: does this charge
 * go through? It returns {@link PaymentResult} (success/failure + its own identity and reference)
 * and MUST NOT update the wallet balance, write ledger rows, or hold business logic - the
 * {@code LedgerService} remains the single source of truth for money movement.
 *
 * <p>The rest of finance (wallet, ledger, income, expense, saving, recurring, auto-save, insights)
 * depends on this interface, never on a concrete provider, so a new provider is a new
 * implementation and nothing else changes. Still simulation-only today - see {@link MockKhqrProvider}.
 */
public interface PaymentProvider {

    /** Authorize a charge and report the outcome. Never moves money itself. */
    PaymentResult charge(PaymentInstruction instruction);
}
