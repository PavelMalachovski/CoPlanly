package com.coparently.app.data.school.edupage

/**
 * What EduPage answers, as the client's tests need it.
 *
 * The pages are cut down to the parts the client reads: the sign-in page's CSRF token, the user
 * page's `userhome(…);` call and its `ASC.gsechash`.
 */
internal object EdupageFixtures {
    // Inferred from the edupage-api 0.13.3 protocol description; not yet recorded live.
    const val LOGIN_HOST = "login1.edupage.org"
    const val SCHOOL_HOST = "zsx.edupage.org"
    const val CSRF = "csrf-7f3a9c"
    const val GSEC_HASH = "gsh-41b2e8"
    const val SESSION_ID = "sess-5d1e0b"
    const val USERNAME = "rodic@example.cz"
    const val PASSWORD = "Tajne-Heslo.42"

    // Inferred from the edupage-api 0.13.3 protocol description; not yet recorded live.
    /** The sign-in page (`/login/?cmd=MainLogin`): the token sits in a script's JSON. */
    val LOGIN_PAGE = """
        <!DOCTYPE html><html><head><title>EduPage</title>
        <script>ASC.req_props = {"edupage":"","lang":"sk","csrftoken":"$CSRF","school_name":""};</script>
        </head><body><form action="/login/edubarLogin.php" method="post"></form></body></html>
    """.trimIndent()

    // Inferred from the edupage-api 0.13.3 protocol description; not yet recorded live.
    /**
     * The user page (`/user`) after a sign-in: `userhome(…);` with tabs and line breaks in its JSON,
     * a brace and an escaped quote inside a string, and a second call after it.
     */
    val USER_PAGE = "<!DOCTYPE html><html><head><script>\n" +
        "ASC.gsechash=\"$GSEC_HASH\";\n" +
        "userhome({\n\t\"userid\":\"Rodic12345\",\n\t\"dp\":{\"year\":2025,\"classes\":{}},\n" +
        "\t\"note\":\"a } brace and a \\\"quote\\\"\",\n" +
        "\t\"_edubar\":{\"selectedChild\":\"4711\"}\n});\n" +
        "$(function(){ init(); });\n" +
        "</script></head><body></body></html>"

    // Inferred from the edupage-api 0.13.3 protocol description; not yet recorded live.
    /** A page of another site: no sign-in token. */
    const val NOT_EDUPAGE_PAGE = "<!DOCTYPE html><html><body>Welcome to our school</body></html>"
}
