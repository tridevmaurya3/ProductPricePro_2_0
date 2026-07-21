package com.example.productprice.model;

public class Product {
    private long id;
    private String category;
    private String name;
    private double vp;
    private int fullPrice;
    private int price15;
    private int price25;
    private int price35;
    private int price42;
    private int price50;
    private boolean active;
    private long updatedAt;

    public Product() {
        active = true;
        updatedAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getVp() { return vp; }
    public void setVp(double vp) { this.vp = vp; }
    public int getFullPrice() { return fullPrice; }
    public void setFullPrice(int fullPrice) { this.fullPrice = fullPrice; }
    public int getPrice15() { return price15; }
    public void setPrice15(int price15) { this.price15 = price15; }
    public int getPrice25() { return price25; }
    public void setPrice25(int price25) { this.price25 = price25; }
    public int getPrice35() { return price35; }
    public void setPrice35(int price35) { this.price35 = price35; }
    public int getPrice42() { return price42; }
    public void setPrice42(int price42) { this.price42 = price42; }
    public int getPrice50() { return price50; }
    public void setPrice50(int price50) { this.price50 = price50; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getPriceForTier(String tier) {
        if (tier == null) return fullPrice;
        switch (tier) {
            case "Price@15": return price15;
            case "Price@25": return price25;
            case "Price@35": return price35;
            case "Price@42": return price42;
            case "Price@50": return price50;
            default: return fullPrice;
        }
    }
}
