package com.doubleshotdevs.brewloop;

public class User {
    public String userID;

    public String fullName, email, role;

    public int pointsBalance;

    public User(){
    }

    public User(String fullName, String email, String role, int pointsBalance) {
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.pointsBalance = pointsBalance;
    }
}
