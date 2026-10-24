package com.doubleshotdevs.brewloop;

public class MenuItem {
        public String itemID;

        public String name, category, description;

        public double basePrice;

        public boolean isAvailable, isCustomizable;

        public MenuItem(){
        }

        public MenuItem(String name, String category, String description, double basePrice, boolean isCustomizable) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.basePrice = basePrice;
            this.isAvailable = true;
            this.isCustomizable = isCustomizable;
        }
    }

