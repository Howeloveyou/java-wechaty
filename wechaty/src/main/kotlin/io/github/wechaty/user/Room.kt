package io.github.wechaty.user

import io.github.wechaty.Accessory
import io.github.wechaty.Puppet
import io.github.wechaty.Wechaty
import io.github.wechaty.filebox.FileBox
import io.github.wechaty.schemas.ContactPayload
import io.github.wechaty.schemas.RoomMemberQueryFilter
import io.github.wechaty.schemas.RoomPayload
import io.github.wechaty.schemas.RoomQueryFilter
import io.github.wechaty.type.Sayable
import io.github.wechaty.utils.FutureUtils
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class Room(wechaty: Wechaty, var id: String? = null) : Accessory(wechaty), Sayable {

    private val puppet: Puppet = wechaty.getPuppet()
    private var payload: RoomPayload? = null

    fun sync(): Future<Void?> {
        return CompletableFuture.completedFuture(null);
    }

    override fun say(something: Any, contact: Contact): Future<Any> {

        var msgId: String? = null

        return CompletableFuture.supplyAsync {
            when (something) {

                is String -> {
                    msgId = puppet.messageSendText(id!!, something).get()

                }

            }

            if (StringUtils.isNotEmpty(msgId)) {
                val message = wechaty.message().load(msgId!!)
                message.ready().get()
                return@supplyAsync message
            }

            return@supplyAsync null

        }
    }

    fun say(something: Any): Future<Any> {

        var msgId: String? = null

        return CompletableFuture.supplyAsync {
            when (something) {

                is String -> {
                    msgId = wechaty.getPuppet().messageSendText(id!!, something).get()
                }
                is FileBox -> {
                    msgId = wechaty.getPuppet().messageSendFile(id!!, something).get()
                }

                is UrlLink -> {
                    msgId = wechaty.getPuppet().messageSendUrl(id!!, something.payload).get()
                }

                is MiniProgram -> {
                    msgId = wechaty.getPuppet().messageSendMiniProgram(id!!, something.payload).get()
                }

                else -> {
                    throw Exception("unknown message")
                }

            }

            if (msgId != null) {
                val msg = wechaty.message().load(msgId!!)
                msg.load(msgId!!)
                return@supplyAsync msg
            }

            return@supplyAsync null
        }
    }


    fun findAll(query: RoomQueryFilter): Future<List<Room>> {
        return CompletableFuture.supplyAsync {
            val roomIdList = puppet.roomSearch(query).get()

            val roomList = roomIdList.map {
                load(it)
            }
            return@supplyAsync roomList
        }

    }

    fun load(id: String): Room {
        val existingRoom = wechaty.getRoomCache().getIfPresent(id)
        if (existingRoom != null) {
            return existingRoom
        }

        val room = Room(wechaty, id)
        wechaty.getRoomCache().put(id, room)
        return room
    }

    fun ready(forceSync: Boolean = false): Future<Void> {
        return CompletableFuture.supplyAsync {
            if (!forceSync && isRead()) {
                return@supplyAsync null
            }

            if (forceSync) {
                puppet.roomPayloadDirty(id!!).get()
                puppet.roomMemberPayloadDirty(id!!).get()
            }

            this.payload = puppet.roomPayload(id!!).get()
            log.info("get room payload is {} by id {}",payload,id)
            if (payload == null) {
                throw Exception("no payload")
            }

            val memberIdList = puppet.roomMemberList(id!!).get()

            memberIdList.map {
                wechaty.contact().load(it)
            }.forEach {
                it.ready()
            }
            return@supplyAsync null
        }
    }

    fun memberAll(query: RoomMemberQueryFilter?):List<Contact>{

        if(query == null){
            return memberList()
        }

        val contactIdList = wechaty.getPuppet().roomMemberSearch(this.id!!, query).get()
        val contactList = contactIdList.map {
            wechaty.contact().load(id!!)
        }

        return contactList

    }

    fun memberList():List<Contact>{

        val memberIdList = wechaty.getPuppet().roomMemberList(this.id!!).get()

        if(CollectionUtils.isEmpty(memberIdList)){
            return listOf()
        }

        val contactList = memberIdList.map {
            wechaty.contact().load(id!!)
        }
        return contactList

    }

    fun alias(contact: Contact):String?{

        val roomMemberPayload = wechaty.getPuppet().roomMemberPayload(this.id!!, contact.id!!).get()

        return roomMemberPayload?.roomAlias


    }

    fun isRead(): Boolean {
        return payload != null
    }

    companion object{
        private val log = LoggerFactory.getLogger(Room::class.java)
    }
}

val ROOM_EVENT_DICT = mapOf(
        "invite" to "tbw",
        "join" to "tbw",
        "leave" to "tbw",
        "message" to "message that received in this room",
        "topic" to "tbw"
)
