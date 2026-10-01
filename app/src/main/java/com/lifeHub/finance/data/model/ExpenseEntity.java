package com.lifeHub.finance.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import androidx.room.ColumnInfo;
import androidx.room.Index;



@Entity(tableName = "expenses", indices = {@Index("timeMillis"), @Index(value = "aiDraftId", unique = true)})
public class ExpenseEntity {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private long amountMinor;
    private Double legacyAmount;
    public long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(long value) { amountMinor=value; }
    public Double getLegacyAmount() { return legacyAmount; }
    public void setLegacyAmount(Double value) { legacyAmount=value; }
    public static ExpenseEntity fromMinor(long amount,String category,String note,long time,String account) {
        ExpenseEntity row=new ExpenseEntity();row.amountMinor=amount;row.category=category;row.note=note;row.timeMillis=time;row.accountType=account;return row;
    }
    private String aiDraftId;
    public String getAiDraftId() { return aiDraftId; }
    public void setAiDraftId(String value) { aiDraftId = value; }

    private String category;

    private String note;

    private long timeMillis;

    @ColumnInfo(name = "account_type")
    private String accountType;


    @Ignore
    public ExpenseEntity(double amount, String category, String note,
                         long timeMillis, String accountType) {
        this.amountMinor = com.lifeHub.finance.domain.Money.fromLegacy(amount);
        this.category = category;
        this.note = note;
        this.timeMillis = timeMillis;
        this.accountType = accountType;
    }


    public ExpenseEntity() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Ignore
    public double getAmount() {
        return amountMinor / 100.0;
    }

    @Ignore
    public void setAmount(double amount) {
        this.amountMinor = com.lifeHub.finance.domain.Money.fromLegacy(amount);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public void setTimeMillis(long timeMillis) {
        this.timeMillis = timeMillis;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

}
