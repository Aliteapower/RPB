package com.rpb.reservation.customer.application;
public record CustomerPrivacyConsentCommand(Boolean accepted,String noticeVersion,String entrySource) {}
