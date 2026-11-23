package tallyvane.knowledge.fixtures

/** The demo of the specification, answer by answer, with what a model would say to each. */
object DemoScript {
    const val FIRST_ANSWER =
        "С 2022 работаю в компании Acme бэкенд-инженером. База постоянно проседала под нагрузкой. " +
            "Я сначала профилировал запросы и нашёл, что ~70% — это повторные чтения каталога. " +
            "Предложил кеш на Redis, согласовал с DBA и внедрил. Нагрузка на базу упала примерно на 40%."
    const val DATES_ANSWER = "Да, я всё ещё там работаю"
    const val TITLE_ANSWER = "Кеш каталога"
    const val DECISION_ANSWER = "Рассматривал Memcached, но Redis уже был в стеке"
    const val NOTHING_MORE = "нет"

    val ANSWERS = listOf(FIRST_ANSWER, DATES_ANSWER, TITLE_ANSWER, DECISION_ANSWER, NOTHING_MORE)

    val EXTRACT =
        listOf(
            """
            {"company":{"n":"Acme","q":"в компании Acme"},
             "role":{"title":"Backend Engineer","start":"2022","q":"С 2022 работаю в компании Acme бэкенд-инженером"},
             "project":{"title":null,"problem":"The database kept degrading under load","q":"База постоянно проседала под нагрузкой"},
             "actions":[
               {"kind":"investigated","verb":"profiled","what":"database queries","finding":"about 70% were repeated catalog reads",
                "q":"Я сначала профилировал запросы и нашёл, что ~70% — это повторные чтения каталога"},
               {"kind":"designed","verb":"proposed","what":"a Redis cache for the catalog","techs":[{"n":"Redis","q":"кеш на Redis"}],"q":"Предложил кеш на Redis"},
               {"kind":"communicated","verb":"agreed the approach with","what":"the DBA team","collabs":[{"n":"DBA","q":"с DBA"}],"q":"согласовал с DBA"},
               {"kind":"built","verb":"implemented","what":"the cache","techs":[{"n":"редис","q":"Redis"}],"q":"внедрил"}
             ],
             "outcomes":[{"what":"database load","dir":"down","val":40,"unit":"%","approx":true,"of":[1,3],"q":"Нагрузка на базу упала примерно на 40%"}]}
            """.trimIndent(),
            """{"role":{"current":true,"q":"всё ещё там работаю"}}""",
            """{"project":{"title":"Catalog cache","q":"Кеш каталога"}}""",
            """
            {"actions":[{"kind":"decided","verb":"chose","what":"Redis over Memcached","techs":[{"n":"Redis","q":"Redis"}],
              "q":"Redis уже был в стеке"}]}
            """.trimIndent(),
        )

    const val BULLETS =
        """{"bullets":[{"text":"Cut database load by about 40% by profiling queries, designing a Redis cache and agreeing the approach with the DBA.","a":["A1","A2","A3","A4","A5"]}]}"""

    const val VACANCY =
        """{"title":"Senior Backend Engineer","reqs":[
            {"skill":"caching","importance":3,"must":true},
            {"skill":"redis","importance":3,"must":true},
            {"skill":"postgresql","importance":2,"must":true},
            {"skill":"kafka","importance":2,"must":true},
            {"skill":"cross-team-collaboration","importance":1,"must":false}],
           "unmapped":["GraphQL"]}"""

    const val VACANCY_TEXT =
        "Senior Backend Engineer. You know caching, Redis, PostgreSQL and Kafka, and work across teams. Bonus: GraphQL."

    fun llm(): ScriptedLlm =
        ScriptedLlm.of("extract" to EXTRACT, "bullets" to listOf(BULLETS), "vacancy" to listOf(VACANCY))
}
