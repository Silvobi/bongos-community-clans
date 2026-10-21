package pl.bongo.clans.claims;

/** Expected validation failure rendered in the viewer's language. */
final class ClaimUiFailure extends IllegalArgumentException {
    final String polish, english;
    ClaimUiFailure(String polish, String english) { super(english); this.polish = polish; this.english = english; }
}
