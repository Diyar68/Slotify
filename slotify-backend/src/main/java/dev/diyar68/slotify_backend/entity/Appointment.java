package dev.diyar68.slotify_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_offering_id", nullable = false)
    private ServiceOffering serviceOffering;

    @NotBlank @Size(max = 100)
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @NotBlank @Email @Size(max = 150)
    @Column(name = "customer_email", nullable = false, length = 150)
    private String customerEmail;

    @NotBlank @Size(max = 30)
    @Column(name = "customer_phone", nullable = false, length = 30)
    private String customerPhone;

    @NotNull
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @NotNull
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    protected Appointment() {
    }

    public Appointment(Provider provider, ServiceOffering serviceOffering, String customerName,
            String customerEmail, String customerPhone, LocalDateTime startTime, LocalDateTime endTime) {
        this.provider = provider;
        this.serviceOffering = serviceOffering;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = AppointmentStatus.BOOKED;
    }

    public Long getId() { return id; }
    public Provider getProvider() { return provider; }
    public ServiceOffering getServiceOffering() { return serviceOffering; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
}
