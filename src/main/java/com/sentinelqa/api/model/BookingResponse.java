package com.sentinelqa.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Envelope returned by POST /booking. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingResponse {

    @JsonProperty("bookingid")
    private Integer bookingid;

    @JsonProperty("booking")
    private Booking booking;

    public Integer getBookingid() {
        return bookingid;
    }

    public void setBookingid(Integer bookingid) {
        this.bookingid = bookingid;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }
}
