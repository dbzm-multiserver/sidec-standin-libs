package ru.sbrf.sidec.helper;

import java.util.UUID;

public class Meme {
    private UUID id;
    private String meme;
    private String memeDescription;

    public Meme() {
    }

    public Meme(UUID id, String meme, String memeDescription) {
        this.id = id;
        this.meme = meme;
        this.memeDescription = memeDescription;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMeme() {
        return meme;
    }

    public void setMeme(String meme) {
        this.meme = meme;
    }

    public String getMemeDescription() {
        return memeDescription;
    }

    public void setMemeDescription(String memeDescription) {
        this.memeDescription = memeDescription;
    }
}