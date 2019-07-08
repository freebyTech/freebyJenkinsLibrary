import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants

void call(String image, String namespaceList) 
{
    stage("Get Approval for Deployment of Previously Built Version")
    {
        // we need a first milestone step so that all jobs entering this stage are tracked an can be aborted if needed
        milestone 1

        timeout(time: 2, unit: 'MINUTES') 
        {
            script 
            {
                env.NAMESPACE = input message: "Deploy ${image} to a specified namespace with a specified version?", ok: 'Select',
                parameters: [
                                string(name: 'VERSION', defaultValue: "1.0.X.X", description: "What version of the image to deploy"),
                                choice(name: 'NAMESPACE', choices: namespaceList, description: "What namespace to deploy to")
                            ]
            }
        }
        // this will kill any job which is still in the input step
        milestone 2
    }
}