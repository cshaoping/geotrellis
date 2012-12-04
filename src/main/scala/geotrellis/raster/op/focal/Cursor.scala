package geotrellis.raster.op.focal

import scala.collection.mutable
import scala.math.{min,max}

import geotrellis._

sealed trait Movement { val isVertical:Boolean }

object Movement { 
  val Up = new Movement { val isVertical = true }
  val Down = new Movement { val isVertical = true }
  val Left = new Movement { val isVertical = false }
  val Right = new Movement { val isVertical = false }
  val NoMovement = new Movement { val isVertical = false }
}
import Movement._

object Cursor {
  def getInt(r:Raster,n:Neighborhood) = {
    val cur = new IntCursor(r,n.extent)
    if(n.hasMask) { cur.setMask(n.mask) }
    cur
  }

  def getDouble(r:Raster,n:Neighborhood) = {
    val cur = new DoubleCursor(r,n.extent)
    if(n.hasMask) { cur.setMask(n.mask) }
    cur
  }
}

trait Cursor {
  def centerOn(x:Int,y:Int):Unit
  def move(m:Movement):Unit
}

trait IntCellSet {
  def foreach(f:Int=>Unit):Unit
  def foreach(cb:IntFocalCellCB):Unit

  /*
   * Get all unmasked cell values covered by the cursor
   * in a sequence. (Non-performant)
   */
  def getAll:Seq[Int] = {
    val result = mutable.Set[Int]()
    foreach { v => result += v }
    result.toSeq
  }

  /*
   * Fold left along all the cell values of the raster
   * which are covered by the cursor and not masked.
   *
   * @param     seed      Seed for the fold operation.
   * @param     f         Function that takes in the seed, or previous computed value,
   *                      and computes a value to be passed into the next iteration.
   */
  def foldLeft(seed:Int)(f:(Int,Int)=>Int) = {
    var a = seed
    foreach { v => a = f(a,v) }
    a
  }
}

trait DoubleCellSet {
  def foreach(f:Double=>Unit):Unit
  def foreach(cb:DoubleFocalCellCB):Unit

  /*
   * Get all unmasked cell values covered by the cursor
   * in a sequence. (Non-performant)
   */
  protected def getAll:Seq[Double] = {
    val result = mutable.Set[Double]()
    foreach { v => result += v }
    result.toSeq
  }

  /*
   * Fold left along all the cell values of the raster
   * which are covered by the cursor and not masked.
   *
   * @param     seed      Seed for the fold operation.
   * @param     f         Function that takes in the seed, or previous computed value,
   *                      and computes a value to be passed into the next iteration.
   */
  protected def foldLeft(seed:Double)(f:(Double,Double)=>Double) = {
    var a = seed
    foreach { v => a = f(a,v) }
    a
  }
}

/**
 * Represents a cursor that can be used to iterate over cells within a focal
 * neighborhood.
 *
 * @param      r                     Raster that this cursor runs over
 * @param      distanceFromCenter    The distance from the focus that the
 *                                   bounding box of this cursor extends.
 *                                   e.g. if the bounding box is 9x9, then
 *                                   the distance from center is 1.
 */
sealed abstract class BaseCursor(r:Raster, distanceFromCenter:Int) extends Cursor {
  private val rows = r.rows
  private val cols = r.cols

  val dim = distanceFromCenter
  private val d = 2*dim + 1

  var mask:CursorMask = null
  private var hasMask = false

  // Values to track the bound of the cursor
  private var _xmin = 0
  private var _xmax = 0
  private var _ymin = 0
  private var _ymax = 0

  // Values to track added\removed values
  private var addedCol = 0
  private var removedCol = 0

  private var addedRow = 0
  private var removedRow = 0

  private var movement = NoMovement

  // Values to track the focus of the cursor
  private var _focusX = 0
  private var _focusY = 0

  def focusX = _focusX
  def focusY = _focusY

  def xmin = _xmin
  def xmax = _xmax
  def ymin = _ymin
  def ymax = _ymax

  def isReset = movement == NoMovement

  /*
   * Centers the cursor on a cell of the raster.
   * Added\Removed cells are not kept track of between centering moves,
   * and centering the cursor resets the state.
   */
  def centerOn(x:Int,y:Int) = { 
    movement = NoMovement
    _focusX = x
    _focusY = y

    setBounds()
  }

  /*
   * Move the cursor one cell space in a horizontal
   * of vertical direction. The cursor will keep track
   * of what cells became added by this move (covered by the cursor
   * or unmasked), and what cells became removed by this move
   * (no longer covered by the cursor or masked when previously unmasked).
   * The cursor will only keep the state of one move, so if two moves
   * are done in a row, the state of the first move is forgotten. Only
   * the difference between the cursor and it's most recent previous position
   * are accounted for.
   *
   * param     m     Movement enum that represents moving the cursor
   *                 Up,Down,Left or Right.
   */
  def move(m:Movement) = {
    movement = m
    m match {
      case Up => 
        addedRow = _ymin - 1
        removedRow = _focusY + dim
        _focusY -= 1
      case Down =>
        addedRow = _ymax + 1
        removedRow = _focusY - dim
        _focusY += 1
      case Left =>
        addedCol = _xmin - 1
        removedCol = _focusX + dim
        _focusX -= 1
      case Right =>
        addedCol = _xmax + 1
        removedCol = _focusX - dim
        _focusX += 1
      case _ => 
    }

    setBounds()
  }

  @inline final private def setBounds() = {
    _xmin = max(0,_focusX - dim)
    _xmax = min(cols - 1, _focusX + dim)
    _ymin = max(0, _focusY - dim)
    _ymax = min(rows - 1, _focusY + dim)
  }

  def setMask(f:(Int,Int) => Boolean) = {
    hasMask = true
    mask = new CursorMask(d,f)
  }

  /*
   * Iterates over all cell values of the raster which
   * are covered by the cursor and not masked.
   *
   * @param     f         Function that receives from each cell
   *                      it's x and y coordinates and it's value.
   */
  protected def foreach(f: (Int,Int)=>Unit):Unit = {
    if(!hasMask) {
      var y = _ymin
      var x = 0
      while(y <= _ymax) {
        x = _xmin
        while(x <= _xmax) {
          f(x,y)
          x += 1
        }
        y += 1
      }
    } else {
      var y = 0
      while(y < d) {
        mask.foreachX(y) { x =>
          val xRaster = x + (_focusX-dim)
          val yRaster = y + (_focusY-dim)
          if(_xmin <= xRaster && xRaster <= _xmax && _ymin <= yRaster && yRaster <= _ymax) {
            f(xRaster,yRaster)
          }
        }
        y += 1
      }
    }
  }

  /*
   * Iterates over all cell values of the raster which
   * are covered by the cursor and not masked, that were exposed
   * as part of the last move of the cursor.
   *
   * For instance, if move(Movement.Up) is called, then there will
   * potentially be a new row that is now covered by the cursor,
   * which are now covered. These values will be included for the
   * iterations of this function, as well any previously masked
   * cell values that were unmasked as part of the move.
   *
   * @param     f         Function that receives from each cell it's
   *                      x and y coordinates and it's value.
   */
  protected def foreachAdded(f:(Int,Int)=>Unit):Unit = {
    if(movement == NoMovement) {
      foreach(f) 
    } else if (movement.isVertical) {
      if(0 <= addedRow && addedRow < rows) {
        if(!hasMask) {
          var x = _xmin
          while(x <= _xmax) {
            f(x,addedRow)
            x += 1
          }
        } else {
          mask.foreachX(addedRow-(_focusY-dim)) { x =>
            val xRaster = x+(_focusX-dim)
            if(0 <= xRaster && xRaster <= rows) {
              f(xRaster,addedRow)
            }
          }
        }
      }        
    } else { // Horizontal
      if(0 <= addedCol && addedCol < cols) {
        if(!hasMask) {
          var y = _ymin
          while(y <= _ymax) {
            f(addedCol,y)
            y += 1
          }
        } else {
          if(movement == Left) {
            mask.foreachWestColumn { y =>
              val yRaster = y+(_focusY-dim)
              if(0 <= yRaster && yRaster < cols) {
                f(addedCol,yRaster)
              }
            }
          } else { // Right
            mask.foreachEastColumn { y =>
              val yRaster = y+(_focusY-dim)
              if(0 <= yRaster && yRaster < cols) {
                f(addedCol,yRaster)
              }
            }
          }
        }
      }        
    }

    if(hasMask) {
      mask.foreachUnmasked(movement) { (x,y) =>
        val xRaster = x+(_focusX-dim)
        val yRaster = y+(_focusY-dim)
        if(0 <= xRaster && xRaster < cols && 0 <= yRaster && yRaster < rows) {
          f(xRaster,yRaster)
        }
      }
    }
  }

  /*
   * Iterates over all cell values of the raster which
   * are no longer covered by the cursor that were not previously masked
   * not masked, or that were masked when previously unmasked,
   * as part of the last move last move of the cursor.
   *
   * For instance, if move(Movement.Up) is called, then there will
   * potentially be a new row at the bottom of the cursor that is now
   * uncovered by the cursor. These values will be included for the
   * iterations of this function, as well any previously unmasked
   * cell values that were masked as part of the move.
   *
   * @param     f         Function that receives from each cell it's
   *                      x and y coordinates and it's value.
   */
  protected def foreachRemoved(f:(Int,Int)=>Unit):Unit = {
    if(movement == NoMovement) { return }

    if(movement.isVertical) {
      if(0 <= removedRow && removedRow < cols) {
        if(!hasMask) {
          var x = _xmin
          while(x <= _xmax) {
            f(x,removedRow)
            x += 1
          }
        } else {
          if(movement == Up) {
            mask.foreachX(d-1) { x =>
              val xRaster = x+(_focusX-dim)
              if(0 <= xRaster && xRaster < cols) {
                f(xRaster,removedRow)
              }
            }
          }
          else { // Down
            mask.foreachX(0) { x =>
              val xRaster = x+(_focusX-dim)
              if(0 <= xRaster && xRaster < cols) {
                f(xRaster,removedRow)
              }
            }
          }
        }
      }
    } else { // Horizontal
      if(0 <= removedCol && removedCol < rows) {
        if(!hasMask) {
          var y = _ymin
          while(y <= _ymax) {
            f(removedCol,y)
            y += 1
          }
        } else {
          if(movement == Left) {
            mask.foreachEastColumn { y =>
              val yRaster = y+(_focusY-dim)
              if(0 <= yRaster && yRaster < cols) {
                f(removedCol,yRaster)
              }
            }
          } else { //Right
            mask.foreachWestColumn { y =>
              val yRaster = y+(_focusY-dim)
              if(0 <= yRaster && yRaster < cols) {
                f(removedCol,yRaster)
              }
            }
          }
        }
      }
    }

    if(hasMask) {
      mask.foreachMasked(movement) { (x,y) =>
        val xRaster = x+(_focusX-dim)
        val yRaster = y+(_focusY-dim)
        if(0 <= xRaster && xRaster < cols && 0 <= yRaster && yRaster < rows) {
          f(xRaster,yRaster)
        }
      }
    }
  }

  def asciiDraw:String = {
    var x = _xmin
    var y = _ymin
    var result = ""

    val mark = (x:Int, y:Int) => result += " " + getStr(x,y) + " "

    while(y <= _ymax) {
      x = _xmin
      while(x <= _xmax) {
        mark(x,y)
	x += 1
      }
      y += 1
      result += "\n"
    }
    result
  }

  def getStr(x:Int,y:Int):String
}

trait IntFocalCellCB { def apply(x:Int,y:Int,v:Int):Unit }
trait DoubleFocalCellCB { def apply(x:Int,y:Int,v:Double):Unit }

trait CursorCellCB { def apply(x:Int,y:Int):Unit }

class IntCursor(r:Raster, dim:Int) extends BaseCursor(r,dim) {
  val raster = r

  val allCells = new IntCellSet {
    def foreach(f:Int=>Unit) = IntCursor.this.foreach { (x,y) => f(get(x,y)) }
    def foreach(cb:IntFocalCellCB) = IntCursor.this.foreach { (x,y) => cb(x,y,get(x,y)) }
  }

  val addedCells = new IntCellSet {
    def foreach(f:Int=>Unit) = IntCursor.this.foreachAdded { (x,y) => f(get(x,y)) }
    def foreach(cb:IntFocalCellCB) = IntCursor.this.foreachAdded { (x,y) => cb(x,y,get(x,y)) }
  }

  val removedCells = new IntCellSet {
    def foreach(f:Int=>Unit) = IntCursor.this.foreachRemoved { (x,y) => f(get(x,y)) }
    def foreach(cb:IntFocalCellCB) = IntCursor.this.foreachRemoved { (x,y) => cb(x,y,get(x,y)) }
  }

  def focusValue:Int = get(focusX,focusY)
  def get(x:Int,y:Int) = { raster.get(x,y) }
  def getStr(x:Int,y:Int):String = { "%d".format(get(x,y)) }
}

class DoubleCursor(r:Raster, dim:Int) extends BaseCursor(r,dim) {
  val raster = r

  val allCells = new DoubleCellSet {
    def foreach(f:Double=>Unit) = DoubleCursor.this.foreach { (x,y) => f(get(x,y)) }
    def foreach(cb:DoubleFocalCellCB) = DoubleCursor.this.foreach { (x,y) => cb(x,y,get(x,y)) }
  }

  val addedCells = new DoubleCellSet {
    def foreach(f:Double=>Unit) = DoubleCursor.this.foreachAdded { (x,y) => f(get(x,y)) }
    def foreach(cb:DoubleFocalCellCB) = DoubleCursor.this.foreachAdded { (x,y) => cb(x,y,get(x,y)) }
  }

  val removedCells = new DoubleCellSet { 
    def foreach(f:Double=>Unit) = DoubleCursor.this.foreachRemoved { (x,y) => f(get(x,y)) }
    def foreach(cb:DoubleFocalCellCB) = DoubleCursor.this.foreachRemoved { (x,y) => cb(x,y,get(x,y)) }
  }

  def focusValue:Double = get(focusX,focusY)
  def get(x:Int,y:Int) = { raster.getDouble(x,y) }
  def getStr(x:Int,y:Int):String = { "%f".format(get(x,y)) }
}

