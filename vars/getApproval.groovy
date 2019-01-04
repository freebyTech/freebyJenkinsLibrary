import com.freebyTech.BuildInfo
import com.freebyTech.BuildConstants

void call(def script, String image, String namespaceList) 
{
    stage("Get Approval for Deployment")
    {
        // we need a first milestone step so that all jobs entering this stage are tracked an can be aborted if needed
        milestone 1

        timeout(time: 10, unit: 'MINUTES') 
        {
            script 
            {
                script.env.NAMESPACE = input message: "Deploy ${image} to a specified namespace?", ok: 'Select',
                parameters: [choice(name: 'NAMESPACE', choices: namespaceList, description: "Whether or not to deploy the ${image} to a namespace")]
            }
        }
        // this will kill any job which is still in the input step
        milestone 2
    }
}