package com.movinsync.shuttle.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Entity
@Table(name = "stops",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"route_id", "sequence_num"}),
           @UniqueConstraint(columnNames = {"route_id", "name"})
       })
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    @JsonIgnore
    private Route route;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Column(name = "arrival_time", length = 10)
    private String arrivalTime;  // stored as "HH:mm"
}
