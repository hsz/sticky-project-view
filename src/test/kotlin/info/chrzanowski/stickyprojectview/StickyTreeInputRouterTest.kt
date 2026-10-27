package info.chrzanowski.stickyprojectview

import com.intellij.ui.PopupHandler
import org.junit.Assert.*
import org.junit.Test
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class StickyTreeInputRouterTest {
    @Test
    fun `label reveal waits for completion and keeps press time horizontal position`() = fixture { tree, pane, panel, items ->
        var result: Int? = null
        val router = StickyTreeInputRouter(tree, panel) { _, x -> result = x }
        pane.viewport.viewPosition = Point(12, 21)
        send(panel, MouseEvent.MOUSE_PRESSED)
        assertNull(result)
        pane.viewport.viewPosition = Point(0, 21)
        send(panel, MouseEvent.MOUSE_RELEASED)
        waitForClick()
        assertEquals(12, result)
        router.dispose()
        assertTrue(items.items().isNotEmpty())
    }

    @Test
    fun `double click drag expansion and disposed gestures do not reveal`() = fixture { tree, _, panel, _ ->
        var reveals = 0
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> reveals++ }
        send(panel, MouseEvent.MOUSE_PRESSED)
        send(panel, MouseEvent.MOUSE_RELEASED)
        send(panel, MouseEvent.MOUSE_PRESSED, count = 2)
        send(panel, MouseEvent.MOUSE_RELEASED, count = 2)
        send(panel, MouseEvent.MOUSE_CLICKED, count = 2)
        send(panel, MouseEvent.MOUSE_PRESSED)
        send(panel, MouseEvent.MOUSE_DRAGGED)
        send(panel, MouseEvent.MOUSE_RELEASED)
        send(panel, MouseEvent.MOUSE_PRESSED, x = 0)
        send(panel, MouseEvent.MOUSE_RELEASED, x = 0)
        waitForClick()
        assertEquals(0, reveals)
        send(panel, MouseEvent.MOUSE_PRESSED)
        send(panel, MouseEvent.MOUSE_RELEASED)
        router.dispose()
        waitForClick()
        assertEquals(0, reveals)
        assertEquals(0, panel.mouseWheelListeners.size)
        assertEquals(0, panel.mouseListeners.size)
        assertEquals(0, panel.mouseMotionListeners.size)
    }

    @Test
    fun `sticky routing preserves metadata and decoration uses viewport coordinates`() = fixture { tree, pane, panel, items ->
        val received = mutableListOf<MouseEvent>()
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { received += e }
            override fun mouseReleased(e: MouseEvent) { received += e }
        })
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> }
        send(panel, MouseEvent.MOUSE_PRESSED, popup = true, button = MouseEvent.BUTTON3)
        val item = items.items().first()
        assertSame(tree, received.single().source)
        assertEquals(tree.getPathBounds(item.treePath).y + 5 - item.yLocation, received.single().y)
        assertEquals(1234L, received.single().`when`)
        assertTrue(received.single().isPopupTrigger)
        send(panel, MouseEvent.MOUSE_RELEASED, popup = true, button = MouseEvent.BUTTON3)
        val decorationY = panel.stickyContentHeight()
        send(panel, MouseEvent.MOUSE_PRESSED, y = decorationY)
        assertEquals(pane.viewport.viewPosition.y + decorationY, received.last().y)
        assertNull(panel.getToolTipText(MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, 60, decorationY, 0, false)))
        router.dispose()
    }

    @Test
    fun `double click delegates native expansion without revealing`() = fixture { tree, _, panel, items ->
        val path = items.items().first().treePath
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> fail("double click reveal") }
        assertTrue(tree.isExpanded(path))
        send(panel, MouseEvent.MOUSE_PRESSED, count = 2)
        send(panel, MouseEvent.MOUSE_RELEASED, count = 2)
        send(panel, MouseEvent.MOUSE_CLICKED, count = 2)
        assertFalse(tree.isExpanded(path))
        waitForClick()
        router.dispose()
    }

    @Test
    fun `single click toggle setting reveals sticky parent instead of collapsing it`() = fixture { tree, _, panel, items ->
        val path = items.items().first().treePath
        tree.toggleClickCount = 1
        var reveals = 0
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> reveals++ }
        send(panel, MouseEvent.MOUSE_PRESSED)
        send(panel, MouseEvent.MOUSE_RELEASED)
        send(panel, MouseEvent.MOUSE_CLICKED)
        assertTrue(tree.isExpanded(path))
        assertEquals(path, tree.selectionPath)
        assertEquals(1, tree.toggleClickCount)
        waitForClick()
        assertEquals(1, reveals)
        send(panel, MouseEvent.MOUSE_PRESSED, x = 0)
        send(panel, MouseEvent.MOUSE_RELEASED, x = 0)
        assertFalse(tree.isExpanded(path))
        router.dispose()
    }

    @Test
    fun `removed captured path is not forwarded and popup is anchored to overlay`() = fixture { tree, pane, panel, items ->
        var popupPoint: Point? = null
        tree.addMouseListener(object : PopupHandler() {
            override fun invokePopup(comp: Component, x: Int, y: Int) { popupPoint = Point(x, y) }
        })
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> fail("stale reveal") }
        send(panel, MouseEvent.MOUSE_PRESSED, popup = true, button = MouseEvent.BUTTON3)
        assertEquals(Point(40, pane.viewport.viewPosition.y + 5), popupPoint)
        val captured = items.items().first().treePath.lastPathComponent as DefaultMutableTreeNode
        (tree.model as DefaultTreeModel).removeNodeFromParent(captured)
        popupPoint = null
        send(panel, MouseEvent.MOUSE_RELEASED, popup = true, button = MouseEvent.BUTTON3)
        assertNull(popupPoint)
        router.dispose()
    }

    @Test
    fun `wheel reaches scroll pane once with precise rotation`() = fixture { tree, pane, panel, _ ->
        var count = 0
        pane.addMouseWheelListener { count++; assertEquals(0.25, it.preciseWheelRotation, 0.0) }
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> }
        panel.dispatchEvent(MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 1, 0, 40, 5, 40, 5,
            0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1, 0.25))
        assertEquals(1, count)
        router.dispose()
    }

    @Test
    fun `push off hit testing excludes decoration and maps the clipped native portion`() = fixture { tree, _, panel, items ->
        val model = tree.model as DefaultTreeModel
        model.insertNodeInto(DefaultMutableTreeNode("next"), model.root as DefaultMutableTreeNode, 1)
        items.recalculate(java.awt.Rectangle(0, 610, 200, 140))
        panel.repaintStickyItems()
        val item = items.items().single()
        assertEquals(-10, item.yLocation)
        assertSame(item, panel.itemAt(40, 0))
        assertSame(item, panel.itemAt(40, 9))
        assertNull(panel.itemAt(40, 10))
        assertNull(panel.itemAt(40, -1))
        var nativeY: Int? = null
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { nativeY = e.y }
        })
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> fail("clipped gesture must not reveal on press") }
        send(panel, MouseEvent.MOUSE_PRESSED, y = 0)
        assertEquals(tree.getPathBounds(item.treePath).y + 10, nativeY)
        router.dispose()
    }

    @Test
    fun `replacing model cancels pending reveal even when it reuses the same nodes`() = fixture { tree, _, panel, _ ->
        var reveals = 0
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> reveals++ }
        send(panel, MouseEvent.MOUSE_PRESSED)
        send(panel, MouseEvent.MOUSE_RELEASED)
        tree.model = DefaultTreeModel(tree.model.root as DefaultMutableTreeNode)
        tree.expandRow(0)
        waitForClick()
        assertEquals(0, reveals)
        router.dispose()
    }

    @Test
    fun `parent masks pushed off child and drag retains captured native path metadata`() = fixture { tree, _, panel, items ->
        val model = tree.model as DefaultTreeModel
        val parent = (model.root as DefaultMutableTreeNode).getChildAt(0) as DefaultMutableTreeNode
        parent.removeAllChildren()
        val child = DefaultMutableTreeNode("child")
        parent.add(child)
        repeat(30) { child.add(DefaultMutableTreeNode("nested-$it")) }
        model.nodeStructureChanged(parent)
        tree.expandRow(0)
        tree.expandRow(1)
        model.insertNodeInto(DefaultMutableTreeNode("next"), model.root as DefaultMutableTreeNode, 1)
        items.recalculate(java.awt.Rectangle(0, 610, 200, 200))
        panel.repaintStickyItems()
        assertEquals(listOf(0, 10), items.items().map { it.yLocation })
        assertEquals(parent, panel.itemAt(40, 15)?.treePath?.lastPathComponent)
        assertEquals(child, panel.itemAt(40, 20)?.treePath?.lastPathComponent)
        var drag: MouseEvent? = null
        tree.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) { drag = e }
        })
        val router = StickyTreeInputRouter(tree, panel) { _, _ -> fail("drag reveal") }
        send(panel, MouseEvent.MOUSE_PRESSED, y = 20)
        send(panel, MouseEvent.MOUSE_DRAGGED, y = 25)
        assertSame(tree, drag!!.source)
        assertEquals(35, drag!!.y)
        assertEquals(1234L, drag!!.`when`)
        assertEquals(MouseEvent.BUTTON1_DOWN_MASK, drag!!.modifiersEx)
        send(panel, MouseEvent.MOUSE_RELEASED, y = 25)
        waitForClick()
        router.dispose()
    }

    private fun fixture(block: (JTree, JScrollPane, StickyTreePanel, VisualStickyTreeItems) -> Unit) {
        SwingUtilities.invokeAndWait {
            val root = DefaultMutableTreeNode("root")
            val parent = DefaultMutableTreeNode("parent")
            root.add(parent)
            repeat(30) { parent.add(DefaultMutableTreeNode("leaf-$it")) }
            val tree = JTree(root).apply { isRootVisible = false; showsRootHandles = true; rowHeight = 20; expandRow(0); setSize(600, 700) }
            val pane = JScrollPane(tree).apply { setSize(200, 140); doLayout(); viewport.viewSize = Dimension(600, 700); viewport.viewPosition = Point(0, 21) }
            val items = VisualStickyTreeItems(tree).apply { recalculate(pane.viewport.viewRect) }
            val panel = StickyTreePanel(tree, items).apply { repaintStickyItems() }
            block(tree, pane, panel, items)
        }
    }

    private fun send(panel: StickyTreePanel, id: Int, x: Int = 40, y: Int = 5, count: Int = 1,
                     popup: Boolean = false, button: Int = MouseEvent.BUTTON1) {
        panel.dispatchEvent(MouseEvent(panel, id, 1234, if (id == MouseEvent.MOUSE_DRAGGED) MouseEvent.BUTTON1_DOWN_MASK else 0,
            x, y, 400, 300, count, popup, if (id == MouseEvent.MOUSE_DRAGGED) MouseEvent.NOBUTTON else button))
    }

    private fun waitForClick() {
        val loop = Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
        val interval = (Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval") as? Int) ?: 500
        Timer(interval + 200) { loop.exit() }.apply { isRepeats = false; start() }
        loop.enter()
    }
}