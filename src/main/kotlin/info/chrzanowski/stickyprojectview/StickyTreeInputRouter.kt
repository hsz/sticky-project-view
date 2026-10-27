package info.chrzanowski.stickyprojectview

import com.intellij.openapi.Disposable
import com.intellij.ui.PopupHandler
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.JViewport
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.ToolTipManager

/** Routes gestures to the native tree, keeping a path rather than a transient row number. */
class StickyTreeInputRouter(
    private val tree: JTree,
    private val panel: StickyTreePanel,
    private val reveal: (StickyTreeItem, Int) -> Unit
) : MouseAdapter(), Disposable {
    private data class Gesture(
        val item: StickyTreeItem?,
        val model: javax.swing.tree.TreeModel,
        val origin: Point,
        val viewportX: Int,
        val label: Boolean,
        var dragged: Boolean = false,
        var popupShown: Boolean = false
    )
    private var gesture: Gesture? = null
    private var completedGesture: Gesture? = null
    private var pendingReveal: Timer? = null
    private var disposed = false
    private val viewport: JViewport?
        get() = SwingUtilities.getAncestorOfClass(JViewport::class.java, tree) as? JViewport

    init {
        panel.addMouseListener(this)
        panel.addMouseMotionListener(this)
        panel.addMouseWheelListener(this)
        ToolTipManager.sharedInstance().registerComponent(panel)
    }

    override fun mousePressed(event: MouseEvent) {
        pendingReveal?.stop()
        completedGesture = null
        val item = panel.itemAt(event.x, event.y)
        val x = viewport?.viewPosition?.x ?: tree.visibleRect.x
        val bounds = item?.let { tree.getPathBounds(it.treePath) }
        gesture = Gesture(item, tree.model, event.point, x, bounds?.contains(x + event.x, bounds.y + event.y - item.yLocation) == true)
        forward(event, gesture)
    }

    override fun mouseDragged(event: MouseEvent) {
        gesture?.dragged = true
        pendingReveal?.stop()
        forward(event, gesture)
    }

    override fun mouseReleased(event: MouseEvent) {
        val completed = gesture
        forward(event, completed)
        gesture = null
        completedGesture = completed
        if (completed == null || completed.dragged || completed.popupShown || !completed.label || event.isPopupTrigger ||
            !SwingUtilities.isLeftMouseButton(event) || event.clickCount != 1 ||
            completed.origin.distance(event.point) > 3 || completed.item == null) return

        val delay = (Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int) ?: 500
        pendingReveal = Timer(delay) {
            if (!disposed && tree.model === completed.model && tree.getRowForPath(completed.item.treePath) >= 0) {
                reveal(completed.item, completed.viewportX)
            }
        }.apply { isRepeats = false; start() }
    }

    override fun mouseClicked(event: MouseEvent) {
        if (event.clickCount > 1) pendingReveal?.stop()
        forward(event, completedGesture)
        completedGesture = null
    }

    override fun mouseMoved(event: MouseEvent) = forward(event, null)
    override fun mouseEntered(event: MouseEvent) = forward(event, null)
    override fun mouseExited(event: MouseEvent) = forward(event, null)

    override fun mouseWheelMoved(event: MouseWheelEvent) {
        val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, tree) as? JScrollPane ?: return
        val point = SwingUtilities.convertPoint(panel, event.point, scrollPane)
        scrollPane.dispatchEvent(MouseWheelEvent(scrollPane, event.id, event.`when`, event.modifiersEx,
            point.x, point.y, event.xOnScreen, event.yOnScreen, event.clickCount, event.isPopupTrigger,
            event.scrollType, event.scrollAmount, event.wheelRotation, event.preciseWheelRotation))
        event.consume()
    }

    private fun forward(event: MouseEvent, captured: Gesture?) {
        if (disposed || captured != null && tree.model !== captured.model) return
        val item = captured?.item ?: if (captured == null) panel.itemAt(event.x, event.y) else null
        val bounds = item?.let { tree.getPathBounds(it.treePath) }
        if (item != null && (bounds == null || tree.getRowForPath(item.treePath) < 0)) return
        val position = viewport?.viewPosition ?: tree.visibleRect.location
        val mapped = MouseEvent(tree, event.id, event.`when`, event.modifiersEx,
            position.x + event.x, if (bounds == null) position.y + event.y else bounds.y + event.y - item!!.yLocation,
            event.xOnScreen, event.yOnScreen, event.clickCount, event.isPopupTrigger, event.button)
        // Do not send hover events to the tree's tooltip manager, or popup triggers at offscreen row coordinates.
        val listeners = if (event.id == MouseEvent.MOUSE_DRAGGED || event.id == MouseEvent.MOUSE_MOVED)
            tree.mouseMotionListeners.toList() else tree.mouseListeners.toList()
        // A single click on a sticky row reveals it, so "Open directories with single click" must not collapse it.
        val toggleClickCount = tree.toggleClickCount
        val suppressToggle = item != null && event.clickCount == 1 && toggleClickCount == 1
        if (suppressToggle) tree.toggleClickCount = 0
        try { dispatch(event, mapped, listeners) } finally { if (suppressToggle) tree.toggleClickCount = toggleClickCount }
        if (event.isPopupTrigger && captured?.popupShown != true) {
            listeners.filterIsInstance<PopupHandler>().forEach {
                it.invokePopup(tree, position.x + event.x, position.y + event.y)
            }
            captured?.popupShown = true
        }
        if (event.id != MouseEvent.MOUSE_MOVED && event.id != MouseEvent.MOUSE_ENTERED && event.id != MouseEvent.MOUSE_EXITED) {
            event.consume()
        }
    }

    private fun dispatch(event: MouseEvent, mapped: MouseEvent, listeners: List<java.util.EventListener>) {
        for (listener in listeners) {
            if (listener is ToolTipManager) continue
            if (listener is PopupHandler && event.isPopupTrigger) continue
            when (event.id) {
                MouseEvent.MOUSE_PRESSED -> (listener as java.awt.event.MouseListener).mousePressed(mapped)
                MouseEvent.MOUSE_RELEASED -> (listener as java.awt.event.MouseListener).mouseReleased(mapped)
                MouseEvent.MOUSE_CLICKED -> (listener as java.awt.event.MouseListener).mouseClicked(mapped)
                MouseEvent.MOUSE_ENTERED -> (listener as java.awt.event.MouseListener).mouseEntered(mapped)
                MouseEvent.MOUSE_EXITED -> (listener as java.awt.event.MouseListener).mouseExited(mapped)
                MouseEvent.MOUSE_DRAGGED -> (listener as java.awt.event.MouseMotionListener).mouseDragged(mapped)
                MouseEvent.MOUSE_MOVED -> (listener as java.awt.event.MouseMotionListener).mouseMoved(mapped)
            }
        }
    }

    override fun dispose() {
        disposed = true
        pendingReveal?.stop()
        gesture = null
        completedGesture = null
        panel.removeMouseListener(this)
        panel.removeMouseMotionListener(this)
        panel.removeMouseWheelListener(this)
        ToolTipManager.sharedInstance().unregisterComponent(panel)
    }
}