package org.vadere.state.psychology.perception.types;

/**
 * Class signals agents a bang - for instance something exploded.
 *
 * This stimulus holds one additional information: a target id
 * which represents the origin of the bang.
 */
public class Bang extends Stimulus {

    // Member Variables
    private int originAsTargetId = -1;
    private double loudness = 1;

    // Constructors
    // Default constructor required for JSON de-/serialization.
    public Bang() { super(); }

    public Bang(double time) {
        super(time);
    }

    public Bang(double time, int originAsTargetId) {
        super(time);

        this.originAsTargetId = originAsTargetId;
    }

    // Getter
    public int getOriginAsTargetId() { return originAsTargetId; }
    public double getLoudness() { return loudness; }

    // Setter
    public void setOriginAsTargetId(int originAsTargetId) { this.originAsTargetId = originAsTargetId; }
    public void setLoudness(double loudness) { this.loudness = loudness; }

}
