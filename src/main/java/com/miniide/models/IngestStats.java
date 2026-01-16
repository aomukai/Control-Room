package com.miniide.models;

public class IngestStats {
    private int filesProcessed;
    private int excerptsStored;
    private int scenesCreated;
    private int cardsCreated;

    public int getFilesProcessed() {
        return filesProcessed;
    }

    public void setFilesProcessed(int filesProcessed) {
        this.filesProcessed = filesProcessed;
    }

    public int getExcerptsStored() {
        return excerptsStored;
    }

    public void setExcerptsStored(int excerptsStored) {
        this.excerptsStored = excerptsStored;
    }

    public int getScenesCreated() {
        return scenesCreated;
    }

    public void setScenesCreated(int scenesCreated) {
        this.scenesCreated = scenesCreated;
    }

    public int getCardsCreated() {
        return cardsCreated;
    }

    public void setCardsCreated(int cardsCreated) {
        this.cardsCreated = cardsCreated;
    }
}
