package com.nowwhat.domain.user.entity;

import com.nowwhat.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private double urgencyWeight = 0.5;

    @Column(nullable = false)
    private double importanceWeight = 0.3;

    @Column(nullable = false)
    private double effortWeight = 0.2;

    protected User() {
    }

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public void updateWeights(double urgencyWeight, double importanceWeight, double effortWeight) {
        this.urgencyWeight = urgencyWeight;
        this.importanceWeight = importanceWeight;
        this.effortWeight = effortWeight;
    }
}
