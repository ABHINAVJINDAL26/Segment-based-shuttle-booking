package com.movinsync.shuttle.service;

import com.movinsync.shuttle.dto.VehicleRequest;
import com.movinsync.shuttle.entity.Vehicle;
import com.movinsync.shuttle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Transactional
    public Vehicle create(VehicleRequest request) {
        vehicleRepository.findByRegistrationNumber(request.getRegistrationNumber())
                .ifPresent(existing -> { throw new IllegalArgumentException("Vehicle registration already exists."); });
        return vehicleRepository.save(Vehicle.builder()
                .registrationNumber(request.getRegistrationNumber())
                .capacity(request.getCapacity())
                .build());
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getAll() {
        return vehicleRepository.findAll();
    }
}