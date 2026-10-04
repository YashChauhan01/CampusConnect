package edu.campusconnect.student;

/** Self-assessed level of knowledge; {@link #weight()} feeds the compatibility model. */
public enum Proficiency {
    BEGINNER(1),
    INTERMEDIATE(2),
    ADVANCED(3);

    private final int weight;

    Proficiency(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /** The lower of two levels; used so a failed verification can only lower, never raise, a self-assessment. */
    public static Proficiency lower(Proficiency a, Proficiency b) {
        return a.weight <= b.weight ? a : b;
    }

    /** One level down, bottoming out at {@link #BEGINNER}. */
    public Proficiency oneLevelDown() {
        return this == ADVANCED ? INTERMEDIATE : BEGINNER;
    }
}
