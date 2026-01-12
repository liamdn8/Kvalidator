package com.nfv.validator;

import com.nfv.validator.cli.CommandLineInterface;
import lombok.extern.slf4j.Slf4j;

/**
 * Main application entry point for KValidator
 * NFV Infrastructure Validation and Configuration Checker Tool
 */
@Slf4j
public class KValidatorApplication {

    public static void main(String[] args) {
        try {
            CommandLineInterface cli = new CommandLineInterface();
            cli.execute(args);
        } catch (Exception e) {
            log.error("Error executing KValidator", e);
            System.err.println("\n❌ Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
