package com.qingyu.hermescompanion.desktop

import java.awt.Point
import java.awt.Rectangle

/** Click, capture and drag are exclusive; a hold emits one start and one release. */
internal class FloatingGestures(val doubleClickMillis:Long=300,val longPressMillis:Long=550,val slop:Int=6) {
    enum class Result { NONE, ASK, CAPTURE, VOICE, VOICE_RELEASE, DRAG }
    private var down:Point?=null
    private var downAt=0L
    private var moved=false
    private var held=false
    private var pending:Point?=null
    private var releasedAt=0L
    fun press(point:Point,now:Long){down=Point(point);downAt=now;moved=false;held=false}
    fun move(point:Point):Result {
        val origin=down?:return Result.NONE
        if(!held&&origin.distance(point)>slop){moved=true;pending=null}
        return if(moved)Result.DRAG else Result.NONE
    }
    fun release(point:Point,now:Long):Result {
        if(down==null)return Result.NONE
        move(point);down=null
        if(held){held=false;return Result.VOICE_RELEASE}
        if(moved)return Result.NONE
        val previous=pending
        if(previous!=null&&now-releasedAt<=doubleClickMillis&&previous.distance(point)<=slop*2){pending=null;return Result.CAPTURE}
        pending=Point(point);releasedAt=now;return Result.NONE
    }
    fun tick(now:Long):Result {
        if(down!=null&&!moved&&!held&&now-downAt>=longPressMillis){held=true;pending=null;return Result.VOICE}
        if(down==null&&pending!=null&&now-releasedAt>=doubleClickMillis){pending=null;return Result.ASK}
        return Result.NONE
    }
    fun cancel(){down=null;pending=null;moved=false;held=false}
    val waiting get()=down!=null||pending!=null
}

internal fun clampFloatingBounds(point:Point,width:Int,height:Int,workAreas:List<Rectangle>):Point {
    if(workAreas.isEmpty())return point
    val center=Point(point.x+width/2,point.y+height/2)
    val area=workAreas.firstOrNull {it.contains(center)}?:workAreas.minBy {r->
        val x=center.x.coerceIn(r.x,r.x+r.width);val y=center.y.coerceIn(r.y,r.y+r.height)
        center.distanceSq(x.toDouble(),y.toDouble())
    }
    return Point(point.x.coerceIn(area.x,(area.x+area.width-width).coerceAtLeast(area.x)),point.y.coerceIn(area.y,(area.y+area.height-height).coerceAtLeast(area.y)))
}

internal fun selectionRectangle(start:Point,end:Point)=Rectangle(minOf(start.x,end.x),minOf(start.y,end.y),kotlin.math.abs(end.x-start.x),kotlin.math.abs(end.y-start.y))
