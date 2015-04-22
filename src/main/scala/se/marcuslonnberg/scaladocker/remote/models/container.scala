package se.marcuslonnberg.scaladocker.remote.models

import org.joda.time.DateTime

sealed trait ContainerId {
  def value: String
}

case class ContainerHashId(hash: String) extends ContainerId {
  override def value = hash

  def shortHash = hash.take(12)

  override def toString = hash
}

case class ContainerName(name: String) extends ContainerId {
  override def value = name

  override def toString = name
}

sealed trait Port {
  def port: Int

  def protocol: String

  override def toString = s"$port/$protocol"
}

object Port {

  case class Tcp(port: Int) extends Port {
    def protocol = "tcp"
  }

  case class Udp(port: Int) extends Port {
    def protocol = "udp"
  }

  def apply(port: Int, protocol: String): Option[Port] = {
    protocol match {
      case "tcp" => Some(Tcp(port))
      case "udp" => Some(Udp(port))
      case _ => None
    }
  }

  private val PortFormat = "(\\d+)/(\\w+)".r

  def unapply(raw: String): Option[Port] = {
    raw match {
      case PortFormat(port, "tcp") => Some(Tcp(port.toInt))
      case PortFormat(port, "udp") => Some(Udp(port.toInt))
      case _ => None
    }
  }
}

case class PortBinding(hostIp: String, hostPort: Int)

/**
 * Configuration for a container.
 *
 * @param image Image to run.
 * @param hostname Container hostname.
 * @param user Username or UID.
 */
case class ContainerConfig(
  image: ImageName,
  hostname: Option[String] = None,
  domainName: Option[String] = None,
  user: Option[String] = None,
  resourceLimits: ContainerResourceLimits = ContainerResourceLimits(),
  standardStreams: StandardStreamsConfig = StandardStreamsConfig(),
  exposedPorts: Seq[Port] = Seq.empty,
  env: Seq[String] = Seq.empty,
  cmd: Seq[String] = Seq.empty,
  volumes: Seq[String] = Seq.empty,
  workingDir: Option[String] = None,
  entryPoint: Option[Seq[String]] = None,
  networkDisabled: Boolean = false,
  onBuild: Seq[String] = Seq.empty
)

/**
 * Configuration options for standard streams.
 *
 * @param attachStdIn Attach to standard input.
 * @param attachStdOut Attach to standard output.
 * @param attachStdErr Attach to standard error.
 * @param tty Allocate a pseudo-TTY.
 * @param openStdin Keep stdin open even if not attached.
 * @param stdinOnce Close stdin when one attached client disconnects.
 */
case class StandardStreamsConfig(
  attachStdIn: Boolean = false,
  attachStdOut: Boolean = false,
  attachStdErr: Boolean = false,
  tty: Boolean = false,
  openStdin: Boolean = false,
  stdinOnce: Boolean = false
)

/**
 * Resource limitations on a container.
 *
 * @param memory Memory limit, in bytes.
 * @param memorySwap Total memory limit (memory + swap), in bytes. Set `-1` to disable swap.
 * @param cpuShares CPU shares (relative weight vs. other containers)
 * @param cpuset CPUs in which to allow execution, examples: `"0-2"`, `"0,1"`.
 */
case class ContainerResourceLimits(
  memory: Long = 0,
  memorySwap: Long = 0,
  cpuShares: Long = 0,
  cpuset: Option[String] = None
)

object ContainerLink {
  def unapply(link: String) = {
    link.split(':') match {
      case Array(container) => Some(ContainerLink(container))
      case Array(container, alias) => Some(ContainerLink(container, Some(alias)))
      case _ => None
    }
  }
}

case class ContainerLink(containerName: String, aliasName: Option[String] = None) {
  def mkString = containerName + aliasName.fold("")(":" + _)
}

case class HostConfig(
  binds: Seq[Volume] = Seq.empty,
  lxcConf: Seq[String] = Seq.empty,
  privileged: Boolean = false,
  portBindings: Option[Map[Port, Seq[PortBinding]]] = None,
  links: Seq[ContainerLink] = Seq.empty,
  publishAllPorts: Boolean = false,
  dns: Seq[String] = Seq.empty,
  dnsSearch: Seq[String] = Seq.empty,
  volumesFrom: Seq[String] = Seq.empty,
  devices: Seq[DeviceMapping] = Seq.empty,
  networkMode: Option[String] = None,
  capAdd: Seq[String] = Seq.empty,
  capDrop: Seq[String] = Seq.empty,
  restartPolicy: RestartPolicy = NeverRestart
)

trait RestartPolicy {
  def name: String
}

case object NeverRestart extends RestartPolicy {
  val name = ""
}

case object AlwaysRestart extends RestartPolicy {
  val name = "always"
}

object RestartOnFailure {
  val name = "on-failure"
}

case class RestartOnFailure(maximumRetryCount: Int = 0) extends RestartPolicy {
  val name = RestartOnFailure.name
}

case class DeviceMapping(pathOnHost: String, pathInContainer: String, cgroupPermissions: String)

case class CreateContainerResponse(id: ContainerHashId, warnings: Seq[String])

case class ContainerState(
  running: Boolean,
  paused: Boolean,
  restarting: Boolean,
  pid: Int,
  exitCode: Int,
  startedAt: Option[DateTime] = None,
  finishedAt: Option[DateTime] = None
)

case class NetworkSettings(
  ipAddress: String,
  ipPrefixLength: Int,
  gateway: String,
  bridge: String,
  ports: Map[Port, Seq[PortBinding]]
)

case class ContainerInfo(
  id: ContainerHashId,
  created: DateTime,
  path: String,
  args: Seq[String],
  config: ContainerConfig,
  state: ContainerState,
  image: String,
  networkSettings: NetworkSettings,
  resolvConfPath: String,
  hostnamePath: String,
  hostsPath: String,
  name: String,
  driver: String,
  execDriver: String,
  mountLabel: Option[String] = None,
  processLabel: Option[String] = None,
  volumes: Seq[Volume] = Seq.empty,
  hostConfig: HostConfig
)

case class Volume(
  hostPath: String,
  containerPath: String,
  rw: Boolean = true
)

case class ContainerStatus(
  command: String,
  created: DateTime,
  id: ContainerHashId,
  image: ImageName,
  names: List[String],
  ports: Map[Port, Seq[PortBinding]] = Map.empty,
  status: String
)
