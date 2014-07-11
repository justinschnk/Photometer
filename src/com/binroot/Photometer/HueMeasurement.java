package com.binroot.Photometer;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Calendar;

public class HueMeasurement {
    private String mUID;
    private double mVal;
    private String mLabel;
    private long mTime;

    public HueMeasurement(String uid, double val, String label, Long time) {
        this.mUID = uid;
        this.mVal = val;
        this.mLabel = label;

        if (time == null) {
            this.mTime = Calendar.getInstance().getTime().getTime();
        } else {
            this.mTime = time;
        }
    }

    public double getVal() {
        return mVal;
    }

    public String getLabel() {
        return mLabel;
    }

    public long getTime() {
        return mTime;
    }

    public String getUID() {
        return mUID;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
                // if deriving: appendSuper(super.hashCode()).
                append(mUID).
                append(mVal).
                append(mLabel).
                append(mTime).
                toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(obj instanceof HueMeasurement))
            return false;

        HueMeasurement rhs = (HueMeasurement) obj;
        return new EqualsBuilder().
                append(mUID, rhs.mUID).
                append(mVal, rhs.mVal).
                append(mLabel, rhs.mLabel).
                append(mTime, rhs.mTime).
                isEquals();
    }

}
