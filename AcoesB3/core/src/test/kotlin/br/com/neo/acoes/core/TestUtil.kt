package br.com.neo.acoes.core

fun resource(name: String): String =
    requireNotNull(object {}.javaClass.classLoader.getResource(name)) { "fixture $name" }.readText()
