package com.sentinelqa.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** Stay window of a booking. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingDates {

    @JsonProperty("checkin")
    private String checkin;

    @JsonProperty("checkout")
    private String checkout;

    public BookingDates() {
    }

    public BookingDates(String checkin, String checkout) {
        this.checkin = checkin;
        this.checkout = checkout;
    }

    public static BookingDates of(LocalDate checkin, LocalDate checkout) {
        return new BookingDates(checkin.toString(), checkout.toString());
    }

    public String getCheckin() {
        return checkin;
    }

    public void setCheckin(String checkin) {
        this.checkin = checkin;
    }

    public String getCheckout() {
        return checkout;
    }

    public void setCheckout(String checkout) {
        this.checkout = checkout;
    }

    public long nights() {
        return java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.parse(checkin), LocalDate.parse(checkout));
    }

    @Override
    public String toString() {
        return checkin + " -> " + checkout;
    }
}
