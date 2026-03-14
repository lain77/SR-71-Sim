package fbw.gameplay;

public class FlightStats {
    private long   startTime;
    private double totalAltitude;
    private int    altSamples;
    private double maxSpeed;
    private double maxMach;
    private double maxAltitude;
    private int    countermeasuresUsed;
    private boolean completed;
    private String  missionName;

    public void begin(String missionName) {
        this.missionName        = missionName;
        this.startTime          = System.currentTimeMillis();
        this.totalAltitude      = 0;
        this.altSamples         = 0;
        this.maxSpeed           = 0;
        this.maxMach            = 0;
        this.maxAltitude        = 0;
        this.countermeasuresUsed = 0;
        this.completed          = false;
    }

    public void sample(fbw.system.FlyByWire.FlightData data) {
        if (data == null) return;
        totalAltitude += data.altitude;
        altSamples++;
        if (data.speed    > maxSpeed)    maxSpeed    = data.speed;
        if (data.mach     > maxMach)     maxMach     = data.mach;
        if (data.altitude > maxAltitude) maxAltitude = data.altitude;
    }

    public void registerCountermeasure() { countermeasuresUsed++; }
    public void setCompleted(boolean b)  { completed = b; }

    public long   getElapsedSeconds()  { return (System.currentTimeMillis() - startTime) / 1000; }
    public double getAvgAltitude()     { return altSamples > 0 ? totalAltitude / altSamples : 0; }
    public double getMaxSpeed()        { return maxSpeed; }
    public double getMaxMach()         { return maxMach; }
    public double getMaxAltitude()     { return maxAltitude; }
    public int    getCountermeasures() { return countermeasuresUsed; }
    public boolean isCompleted()       { return completed; }
    public String  getMissionName()    { return missionName; }

    public String getElapsedFormatted() {
        long s = getElapsedSeconds();
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}