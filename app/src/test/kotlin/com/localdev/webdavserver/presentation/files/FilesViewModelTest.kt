package com.localdev.webdavserver.presentation.files

import androidx.lifecycle.ViewModelStore
import com.localdev.webdavserver.data.local.model.FileNode
import com.localdev.webdavserver.data.local.model.ServerConfig
import com.localdev.webdavserver.data.server.FileChangeBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val config = MutableStateFlow(ServerConfig(rootFolderUri = "/root"))
    private val scans = mutableListOf<String>()
    private var contents = emptyList<FileNode>()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    private fun model() = FilesViewModel(config, { uri, _ ->
        scans += uri
        contents
    }, false).also { store.put("files", it) }

    @Test fun `first entry loads once but resumes and elapsed time never scan`() = runTest(dispatcher) {
        val vm = model()
        runCurrent()
        assertEquals(listOf("/root"), scans)
        vm.navigateTo(0); runCurrent()
        assertEquals(1, scans.size)
        repeat(3) { vm.onScreenResumed() }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, scans.size)
        config.value = config.value.copy(port = 9090)
        runCurrent()
        assertEquals("Empty directories must not be treated as unloaded", 1, scans.size)
    }

    @Test fun `manual refresh and directory navigation load the selected directory`() = runTest(dispatcher) {
        val vm = model()
        runCurrent()
        vm.refresh(); runCurrent()
        vm.open(FileNode("/root/child", "child", true, 0, 0, "inode/directory")); runCurrent()
        vm.navigateTo(0); runCurrent()
        assertEquals(listOf("/root", "/root", "/root/child", "/root"), scans)
        config.value = config.value.copy(rootFolderUri = "/other")
        runCurrent()
        assertEquals("/other", scans.last())
    }

    @Test fun `file change events refresh without a live toggle and coalesce bursts`() = runTest(dispatcher) {
        val vm = model()
        runCurrent()
        contents = listOf(FileNode("/root/new.txt", "new.txt", false, 4, 1, "text/plain"))
        repeat(4) { FileChangeBus.notifyChanged() }
        runCurrent()
        advanceTimeBy(201); runCurrent()
        assertEquals(2, scans.size)
        assertEquals(contents, vm.state.value.nodes)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(2, scans.size)
    }

    @Test fun `hidden settings filter the snapshot without rescanning or losing current directory`() = runTest(dispatcher) {
        contents = listOf(FileNode("/root/.hidden", ".hidden", false, 0, 0, "text/plain"))
        val vm = model(); runCurrent()
        assertTrue(vm.state.value.filtered.isEmpty())
        config.value = config.value.copy(showHiddenFiles = true)
        runCurrent()
        assertEquals(contents, vm.state.value.filtered)
        assertEquals(1, scans.size)
        config.value = config.value.copy(rootFolderUri = null)
        runCurrent()
        vm.refresh(); vm.onScreenResumed(); FileChangeBus.notifyChanged()
        advanceTimeBy(300); runCurrent()
        assertEquals(1, scans.size)
        assertTrue(vm.state.value.noRoot)
    }
}
