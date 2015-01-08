package org.peerbox;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hive2hive.core.api.interfaces.IFileConfiguration;
import org.hive2hive.core.exceptions.NoPeerConnectionException;
import org.hive2hive.core.exceptions.NoSessionException;
import org.hive2hive.core.file.FileUtil;
import org.hive2hive.core.model.PermissionType;
import org.hive2hive.core.processes.files.list.FileNode;
import org.hive2hive.core.processes.files.recover.IVersionSelector;
import org.hive2hive.processframework.exceptions.InvalidProcessStateException;
import org.hive2hive.processframework.exceptions.ProcessExecutionException;
import org.hive2hive.processframework.interfaces.IProcessComponent;
import org.hive2hive.processframework.interfaces.IProcessComponentListener;
import org.hive2hive.processframework.interfaces.IProcessEventArgs;
import org.peerbox.app.manager.AbstractManager;
import org.peerbox.app.manager.node.INodeManager;
import org.peerbox.h2h.ProcessHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;


public class FileManager extends AbstractManager implements IPeerboxFileManager {

	private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

	@Inject
	public FileManager(final INodeManager h2hManager, final IUserConfig userConfig) {
		// TODO(AA): give message bus instance and implement events?
		// maybe can implement the file events also elsewhere, e.g. action executor
		super(h2hManager, userConfig, null); 
	}
	
	@Override
	public ProcessHandle<Void> add(final File file) throws NoSessionException, NoPeerConnectionException {
		logger.debug("ADD - {}", file);
		IProcessComponent<Void> component = getFileManager().createAddProcess(file);
		component.attachListener(new FileOperationListener(file));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}

	@Override
	public ProcessHandle<Void> update(final File file) throws NoSessionException, NoPeerConnectionException {
		logger.debug("UPDATE - {}", file);
		IProcessComponent<Void> component = getFileManager().createUpdateProcess(file);
		component.attachListener(new FileOperationListener(file));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}

	@Override
	public ProcessHandle<Void> delete(final File file) throws NoSessionException, NoPeerConnectionException {
		logger.debug("DELETE - {}", file);
		IProcessComponent<Void> component = getFileManager().createDeleteProcess(file);
		component.attachListener(new FileOperationListener(file));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}

	@Override
	public ProcessHandle<Void> move(final File source, final File destination) throws NoSessionException, NoPeerConnectionException {
		logger.debug("MOVE - from: {}, to: {}", source, destination);
		IProcessComponent<Void> component = getFileManager().createMoveProcess(source, destination);
		component.attachListener(new FileOperationListener(source));
		component.attachListener(new FileOperationListener(destination));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}

	@Override
	public ProcessHandle<Void> download(final File file) throws NoSessionException, NoPeerConnectionException {
		logger.debug("DOWNLOAD - {}", file);
		IProcessComponent<Void> component = getFileManager().createDownloadProcess(file);
		component.attachListener(new FileOperationListener(file));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}

	@Override
	public ProcessHandle<Void> recover(final File file, final IVersionSelector versionSelector) throws NoSessionException, NoPeerConnectionException, IllegalArgumentException {
		logger.debug("RECOVER - {}", file);
		IProcessComponent<Void> component = getFileManager().createRecoverProcess(file, versionSelector);
		component.attachListener(new FileOperationListener(file));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}
	
	@Override
	public ProcessHandle<Void> share(final File folder, final String userId, final PermissionType permission) throws IllegalArgumentException, NoSessionException, NoPeerConnectionException, InvalidProcessStateException, ProcessExecutionException {
		logger.debug("SHARE - User: '{}', Permission: '{}', Folder: '{}'", userId, permission.name(), folder);
		IProcessComponent<Void> component = getFileManager().createShareProcess(folder, userId, permission);
		component.attachListener(new FileOperationListener(folder));
		ProcessHandle<Void> handle = new ProcessHandle<Void>(component);
		return handle;
	}
	
	@Override
	public FileNode listFiles() throws NoPeerConnectionException, NoSessionException, InvalidProcessStateException, ProcessExecutionException {
		IProcessComponent<FileNode> component = getFileManager().createFileListProcess();
		// TODO: execute should be outside?
		return component.execute();
	}
	
	@Override
	public boolean existsRemote(final Path path) {
		FileNode item = null;
		FileNode list = null;
		try {
			list = listFiles();
			item = getFileNodeByPath(list, path);
		} catch (NoPeerConnectionException | NoSessionException | 
				InvalidProcessStateException | ProcessExecutionException e) {
			item = null;
			logger.warn("Could not check existsRemote - Exception: {}", e.getMessage(), e);
		}
		
		return item != null ? true : false;
	}
	
	/**
	 * Searches a FileNode given a path.
	 * @param index the file index, e.g. the root node
	 * @param path the path for which the node should be searched
	 * @return the file node corresponding to the given path or null if none exists
	 */
	private FileNode getFileNodeByPath(final FileNode index, final Path path) {
		Path current = path;
		List<String> pathItems = new ArrayList<>();
		while (current != null && !getRootPath().equals(current)) {
			pathItems.add(current.getFileName().toString());
			current = current.getParent();
		}
		Collections.reverse(pathItems);
		
		FileNode currentNode = index;
		for (String pathItem : pathItems) {
			FileNode child = getChildByName(currentNode.getChildren(), pathItem);
			if (child == null) {
				return null;
			}
			
			currentNode = child;
			if(child.isFile()) {
				break; // cannot go further down the tree
			}
		}
		// it may be the case that we did not consider all pathItems
		if(currentNode.getFile().toPath().equals(path)) {
			return currentNode;
		} else {
			return null;
		}
	}
	
	/**
	 * Searches a child node in a list with a given name
	 * @param children list of child nodes
	 * @param name the name to search
	 * @return the FileNode or null if none exists
	 */
	private FileNode getChildByName(final List<FileNode> children, final String name) {
		for (FileNode child : children) {
			if (child.getName().equalsIgnoreCase(name)) {
				return child;
			}
		}
		return null;
	}

	@Override
	public boolean isSmallFile(final Path path) {
		IFileConfiguration fileConfig = getFileConfiguration();
		return (BigInteger.valueOf(FileUtil.getFileSize(path.toFile()))
				.compareTo(fileConfig.getMaxFileSize()) == 1);
	}
	
	@Override
	public boolean isLargeFile(final Path path) {
		return !isSmallFile(path);
	}
	
	private final class FileOperationListener implements IProcessComponentListener {
		private final File path;

		public FileOperationListener(final File path) {
			this.path = path;
		}

		@Override
		public void onExecuting(IProcessEventArgs args) {
			logger.trace("onExecuting: {}", path);
		}

		@Override
		public void onRollbacking(IProcessEventArgs args) {
			logger.trace("onRollbacking: {}", path);
		}

		@Override
		public void onPaused(IProcessEventArgs args) {
			logger.trace("onPaused: {}", path);
		}

		@Override
		public void onExecutionSucceeded(IProcessEventArgs args) {
			logger.trace("onExecutionSucceeded: {}", path);
		}

		@Override
		public void onExecutionFailed(IProcessEventArgs args) {
			logger.trace("onExecutionFailed: {}", path);
		}

		@Override
		public void onRollbackSucceeded(IProcessEventArgs args) {
			logger.trace("onRollbackSucceeded: {}", path);
		}

		@Override
		public void onRollbackFailed(IProcessEventArgs args) {
			logger.trace("onRollbackFailed: {}", path);
		}
	}
}