package com.habittracker;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:api_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class DavamlyApiTests extends DavamlyApiContract {}
