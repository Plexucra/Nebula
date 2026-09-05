package de.nebula.model;

public class Transaction {
  public String id;
  public String fromWalletId;
  public String toWalletId;
  public double amount;
  public TransactionReason reason;
  public long at;
  public String note;
}
