/**
 * Admin Dashboard module - the HTTP boundary for {@code /api/v1/admin/**}.
 *
 * <p>Design: {@code docs/admin-dashboard.md} (approved SSOT). The four rules below are §1.6 of
 * that document. They are what keeps a modular monolith modular, and they are the first thing
 * to erode under deadline pressure.
 *
 * <h2>R1 - This package owns the entire admin HTTP surface</h2>
 * Every {@code /api/v1/admin/**} endpoint lives here. Nothing admin-specific is added to
 * {@code modules/community}, {@code modules/finance} and so on. One place to audit the whole
 * privileged surface, one place where permission annotations live.
 *
 * <p>Exception, temporary: {@code OpportunityController} and {@code DiscussionController}
 * already carry {@code hasRole('ADMIN')} routes under {@code /api/v1/community/**}. They stay
 * as they are until Phase 7 relocates them. Do not "tidy" them mid-MVP.
 *
 * <h2>R2 - Delegate to domain services; never reimplement them</h2>
 * Admin services call the existing domain services rather than reaching into their
 * repositories. Where an admin operation needs behaviour a domain service does not expose
 * (for example "delete any post, not just my own"), <strong>add the method to that domain
 * service</strong> - do not inject its repository here.
 *
 * <p>Precedent: {@code MobileHomeBffService} aggregates across domains through services, and
 * {@code SavedItemPreviewResolver} is documented as the one place the profile module reads
 * other domains. This module follows the same accepted pattern.
 *
 * <h2>R3 - Analytics is the one sanctioned exception, and it is read-only</h2>
 * {@code admin/analytics/repository} may issue JPQL/native aggregates across domain tables,
 * because {@code COUNT(*) GROUP BY} cannot sensibly go through a domain service. Those
 * repositories return projections or records, never entities, and never write. The coupling
 * is accepted and deliberately bounded to that one package.
 *
 * <h2>R4 - Never expose entities</h2>
 * All responses are records under {@code dto/response}. Admin DTOs are deliberately richer
 * than mobile ones - they carry {@code accountStatus}, {@code emailVerified},
 * {@code deletedAt}, {@code tokenVersion} - which is precisely why they must be separate
 * types and must never be returned from a {@code /api/v1/**} mobile route.
 */
package kh.edu.acleda.campuslink.modules.admin;
