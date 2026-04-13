package ru.ainetico.scanprice.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RemoteEngine
