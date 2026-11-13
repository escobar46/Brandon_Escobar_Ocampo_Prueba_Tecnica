package com.brandon.polizas.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Poliza de arrendamiento. Invariante: {@code prima = canonMensual x vigenciaMeses}.
 * Los importes son {@link BigDecimal} con 2 decimales para evitar errores de redondeo.
 */
@Entity
@Table(name = "polizas")
@Getter
@Setter
@NoArgsConstructor
public class Poliza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPoliza tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPoliza estado;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private Integer vigenciaMeses;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal canonMensual;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal prima;

    /** Al guardar la poliza se guardan tambien sus riesgos (cascade). */
    @OneToMany(mappedBy = "poliza", cascade = CascadeType.ALL)
    private List<Riesgo> riesgos = new ArrayList<>();

    /** Bloqueo optimista: si dos operaciones guardan la misma poliza a la vez, la segunda falla con 409. */
    @Version
    private Long version;
}
