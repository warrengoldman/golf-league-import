package com.sdg.golf.service;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class ImportRoundServiceTest {

    @Test
    void getDateFromSheetName() throws Exception {
        ImportRoundService importRoundService = new ImportRoundService();
        Date d = importRoundService.getDateFromSheetName("24-8-1");
        assertNotNull(d);
        assertEquals("2024-08-01", new SimpleDateFormat("yyyy-MM-dd").format(d));
    }
}