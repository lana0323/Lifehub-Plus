package com.lifeHub.schedule.data;

public class Event {
    private long id;
    private String date;
    private String time;
    private String title;
    private String description;
    private long memoId;

    public Event(long id, String date, String time, String title, String description, long memoId) {
        this(id, date, time, title, description);
        this.memoId = memoId;
    }

    public long getMemoId() { return memoId; }

    public Event(long id, String date, String time, String title, String description) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.title = title;
        this.description = description;
    }

    public long getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
