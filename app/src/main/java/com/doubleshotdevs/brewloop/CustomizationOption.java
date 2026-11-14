package com.doubleshotdevs.brewloop;

public class CustomizationOption {
    public String optionID;
    public String optionType, optionName;
    public double priceChange;

    public CustomizationOption() {
    }

    public CustomizationOption(String optionType, String optionName, double priceChange) {
        this.optionType = optionType;
        this.optionName = optionName;
        this.priceChange = priceChange;
    }
}
