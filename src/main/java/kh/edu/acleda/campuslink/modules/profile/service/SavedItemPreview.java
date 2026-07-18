package kh.edu.acleda.campuslink.modules.profile.service;

/** The bit of a saved item needed to draw a row: no domain object leaks out. */
public record SavedItemPreview(
    String title,
    String subtitle,
    String imageUrl
) {
}
