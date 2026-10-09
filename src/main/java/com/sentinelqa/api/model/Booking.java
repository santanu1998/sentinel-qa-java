package com.sentinelqa.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.Objects;

/** Booking resource exposed by the reservations service. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Booking {

    @JsonProperty("firstname")
    private String firstname;

    @JsonProperty("lastname")
    private String lastname;

    @JsonProperty("totalprice")
    private Integer totalprice;

    @JsonProperty("depositpaid")
    private Boolean depositpaid;

    @JsonProperty("bookingdates")
    private BookingDates bookingdates;

    @JsonProperty("additionalneeds")
    private String additionalneeds;

    public Booking() {
    }

    /** Fluent builder keeps test data readable at the call site. */
    public static Builder builder() {
        return new Builder();
    }

    public static Booking sample() {
        return builder()
                .firstname("Santanu")
                .lastname("Singha")
                .totalprice(14500)
                .depositpaid(true)
                .dates(LocalDate.now().plusDays(7), LocalDate.now().plusDays(10))
                .additionalneeds("Late checkout")
                .build();
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public Integer getTotalprice() {
        return totalprice;
    }

    public void setTotalprice(Integer totalprice) {
        this.totalprice = totalprice;
    }

    public Boolean getDepositpaid() {
        return depositpaid;
    }

    public void setDepositpaid(Boolean depositpaid) {
        this.depositpaid = depositpaid;
    }

    public BookingDates getBookingdates() {
        return bookingdates;
    }

    public void setBookingdates(BookingDates bookingdates) {
        this.bookingdates = bookingdates;
    }

    public String getAdditionalneeds() {
        return additionalneeds;
    }

    public void setAdditionalneeds(String additionalneeds) {
        this.additionalneeds = additionalneeds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Booking other)) {
            return false;
        }
        return Objects.equals(firstname, other.firstname)
                && Objects.equals(lastname, other.lastname)
                && Objects.equals(totalprice, other.totalprice)
                && Objects.equals(depositpaid, other.depositpaid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstname, lastname, totalprice, depositpaid);
    }

    @Override
    public String toString() {
        return "Booking{%s %s, total=%s, deposit=%s, dates=%s}"
                .formatted(firstname, lastname, totalprice, depositpaid, bookingdates);
    }

    public static final class Builder {

        private final Booking booking = new Booking();

        public Builder firstname(String value) {
            booking.firstname = value;
            return this;
        }

        public Builder lastname(String value) {
            booking.lastname = value;
            return this;
        }

        public Builder totalprice(Integer value) {
            booking.totalprice = value;
            return this;
        }

        public Builder depositpaid(Boolean value) {
            booking.depositpaid = value;
            return this;
        }

        public Builder dates(LocalDate checkin, LocalDate checkout) {
            booking.bookingdates = BookingDates.of(checkin, checkout);
            return this;
        }

        public Builder dates(BookingDates value) {
            booking.bookingdates = value;
            return this;
        }

        public Builder additionalneeds(String value) {
            booking.additionalneeds = value;
            return this;
        }

        public Booking build() {
            return booking;
        }
    }
}
