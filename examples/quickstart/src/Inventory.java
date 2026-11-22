package org.example.quickstart;

public final class Inventory {
    private int itemCount;

    public Inventory(int itemCount) {
        this.itemCount = itemCount;
    }

    public int itemCount() {
        return itemCount;
    }

    public void addItem() {
        itemCount++;
    }
}
