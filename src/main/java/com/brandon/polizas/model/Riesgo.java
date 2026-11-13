package com.brandon.polizas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Inmueble asegurado dentro de una poliza: un arrendatario y una direccion. */
@Entity
@Table(name = "riesgos")
@Getter
@Setter
@NoArgsConstructor
public class Riesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poliza_id")
    private Poliza poliza;

    @Column(nullable = false)
    private String arrendatario;

    @Column(nullable = false)
    private String direccionInmueble;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoRiesgo estado;

    /** Bloqueo optimista, igual que en {@link Poliza}. */
    @Version
    private Long version;
}
