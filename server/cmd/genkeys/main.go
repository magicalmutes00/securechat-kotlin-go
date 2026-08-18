package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"fmt"
	"os"
)

func main() {
	fmt.Println("=== SecureChat RSA Key Generator ===")
	fmt.Println()

	// Generate access token keys
	fmt.Println("Generating Access Token Keys (2048-bit RSA)...")
	accessPrivKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to generate access private key: %v\n", err)
		os.Exit(1)
	}

	// Generate refresh token keys
	fmt.Println("Generating Refresh Token Keys (2048-bit RSA)...")
	refreshPrivKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to generate refresh private key: %v\n", err)
		os.Exit(1)
	}

	// Marshal private keys as PKCS8
	accessPrivASN1, err := x509.MarshalPKCS8PrivateKey(accessPrivKey)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to marshal access key: %v\n", err)
		os.Exit(1)
	}
	accessPrivPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "PRIVATE KEY",
		Bytes: accessPrivASN1,
	})

	refreshPrivASN1, err := x509.MarshalPKCS8PrivateKey(refreshPrivKey)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to marshal refresh key: %v\n", err)
		os.Exit(1)
	}
	refreshPrivPEM := pem.EncodeToMemory(&pem.Block{
		Type:  "PRIVATE KEY",
		Bytes: refreshPrivASN1,
	})

	// Base64 encode for .env
	accessPrivB64 := base64.StdEncoding.EncodeToString(accessPrivPEM)
	refreshPrivB64 := base64.StdEncoding.EncodeToString(refreshPrivPEM)

	fmt.Println()
	fmt.Println("=== Environment Variables for .env ===")
	fmt.Println()
	fmt.Println("Copy these to your server/.env file:")
	fmt.Println()
	fmt.Printf("JWT_ACCESS_SECRET=%s\n", accessPrivB64)
	fmt.Printf("JWT_REFRESH_SECRET=%s\n", refreshPrivB64)
	fmt.Println()
	fmt.Println("Keys generated successfully!")
	fmt.Println()
	fmt.Println("IMPORTANT: Keep the private keys secure! Never commit them to git.")
}