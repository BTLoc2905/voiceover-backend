package com.voiceover.backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "route_stop", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"route_id", "stop_order"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "routestop_id")
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "poi_id", nullable = false)
    private Long poiId;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;
}