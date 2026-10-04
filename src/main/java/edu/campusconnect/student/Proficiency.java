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
}
