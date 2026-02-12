package com.example.ava.esphome.entities

import com.example.esphomeproto.api.EntityCategory
import com.example.esphomeproto.api.ListEntitiesRequest
import com.example.esphomeproto.api.SwitchCommandRequest
import com.example.esphomeproto.api.listEntitiesSwitchResponse
import com.example.esphomeproto.api.switchStateResponse
import com.google.protobuf.MessageLite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class SwitchEntity(
    val key: Int,
    val name: String,
    val objectId: String,
    val entityCategory: EntityCategory = EntityCategory.ENTITY_CATEGORY_CONFIG,
    val getState: Flow<Boolean>,
    val setState: suspend (Boolean) -> Unit
) : Entity {
    override fun handleMessage(message: MessageLite) = flow {
        when (message) {
            is ListEntitiesRequest -> emit(listEntitiesSwitchResponse {
                key = this@SwitchEntity.key
                name = this@SwitchEntity.name
                objectId = this@SwitchEntity.objectId
                entityCategory = this@SwitchEntity.entityCategory
            })

            is SwitchCommandRequest -> {
                if (message.key == key)
                    setState(message.state)
            }
        }
    }

    override fun subscribe() = getState.map {
        switchStateResponse {
            key = this@SwitchEntity.key
            this.state = it
        }
    }
}