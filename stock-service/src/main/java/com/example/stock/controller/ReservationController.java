package com.example.stock.controller;

import com.example.stock.dto.ReservationRequest;
import com.example.stock.dto.ReservationResponse;
import com.example.stock.service.ReservationService;
import com.example.stock.service.ReservationService.ReserveResult;
import com.example.stock.util.AppConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservation API for the order-service (internal: the gateway blocks it).
 * Flow: reserve when an order is placed -> confirm when it completes,
 * or release when it is cancelled.
 */
@RestController
@RequestMapping("/api/v1/stock/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** 201 = newly reserved, 200 = this order was already reserved (safe retry), 409 = not enough stock. */
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        ReserveResult result = reservationService.reserve(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.reservation());
    }

    @GetMapping("/{orderId}")
    public ReservationResponse get(@PathVariable @Pattern(regexp = AppConstants.ORDER_ID_REGEX) String orderId) {
        return reservationService.get(orderId);
    }

    @PostMapping("/{orderId}/confirm")
    public ReservationResponse confirm(@PathVariable @Pattern(regexp = AppConstants.ORDER_ID_REGEX) String orderId) {
        return reservationService.confirm(orderId);
    }

    @PostMapping("/{orderId}/release")
    public ReservationResponse release(@PathVariable @Pattern(regexp = AppConstants.ORDER_ID_REGEX) String orderId) {
        return reservationService.release(orderId);
    }
}
