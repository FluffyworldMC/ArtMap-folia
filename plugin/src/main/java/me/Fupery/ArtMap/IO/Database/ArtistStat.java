package me.Fupery.ArtMap.IO.Database;

import java.util.UUID;

/** Aggregated artwork information used to sort the artist menu without loading every artwork. */
public final class ArtistStat {
    private final UUID artist;
    private int artworks;
    private long newestDay = Long.MIN_VALUE;
    private long oldestDay = Long.MAX_VALUE;

    ArtistStat(UUID artist) {
        this.artist = artist;
    }

    void add(long day) {
        artworks++;
        if (day > newestDay) newestDay = day;
        if (day < oldestDay) oldestDay = day;
    }

    public UUID getArtist() {
        return artist;
    }

    public int getArtworks() {
        return artworks;
    }

    /** Epoch day of the newest artwork, or {@link Long#MIN_VALUE} when unknown. */
    public long getNewestDay() {
        return newestDay;
    }

    /** Epoch day of the oldest artwork, or {@link Long#MAX_VALUE} when unknown. */
    public long getOldestDay() {
        return oldestDay;
    }
}
